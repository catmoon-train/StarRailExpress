package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerAFKComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.StreamingSpectatorPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class StreamingSpectatorCommand {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static final int INVENTORY_SYNC_INTERVAL_TICKS = 20;
    private static boolean eventsRegistered;

    private StreamingSpectatorCommand() {
    }

    public static void registerEvents() {
        if (eventsRegistered) {
            return;
        }
        eventsRegistered = true;
        ServerTickEvents.END_SERVER_TICK.register(StreamingSpectatorCommand::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Session session = SESSIONS.remove(handler.getPlayer().getUUID());
            if (session != null) {
                restoreOriginalRole(handler.getPlayer(), session);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("挂播")
                .executes(StreamingSpectatorCommand::toggle)
                .then(Commands.literal("start").executes(StreamingSpectatorCommand::start))
                .then(Commands.literal("stop").executes(StreamingSpectatorCommand::stop)));
        dispatcher.register(Commands.literal("tmm:guabo")
                .executes(StreamingSpectatorCommand::toggle)
                .then(Commands.literal("start").executes(StreamingSpectatorCommand::start))
                .then(Commands.literal("stop").executes(StreamingSpectatorCommand::stop)));
    }

    private static int toggle(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (Exception e) {
            context.getSource().sendFailure(Component.translatable("commands.sre.streaming_spectator.player_only"));
            return 0;
        }
        if (SESSIONS.containsKey(player.getUUID())) {
            return stop(context);
        }
        return start(context);
    }

    private static int start(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (Exception e) {
            context.getSource().sendFailure(Component.translatable("commands.sre.streaming_spectator.player_only"));
            return 0;
        }

        if (SESSIONS.containsKey(player.getUUID())) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.sre.streaming_spectator.already_active"), false);
            return 1;
        }

        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameComponent.isRunning()) {
            context.getSource().sendFailure(Component.translatable("commands.sre.streaming_spectator.game_not_running"));
            return 0;
        }

        Session session = new Session(gameComponent.getRole(player), player.gameMode.getGameModeForPlayer());
        SESSIONS.put(player.getUUID(), session);
        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        }

        if (!attachToRandomTarget(player, session, null)) {
            SESSIONS.remove(player.getUUID());
            restoreOriginalRole(player, session);
            restoreOriginalGameMode(player, session);
            PacketTracker.sendToClient(player, StreamingSpectatorPayload.stop());
            context.getSource().sendFailure(Component.translatable("commands.sre.streaming_spectator.no_target"));
            return 0;
        }

        context.getSource().sendSuccess(() ->
                Component.translatable("commands.sre.streaming_spectator.started"), false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (Exception e) {
            context.getSource().sendFailure(Component.translatable("commands.sre.streaming_spectator.player_only"));
            return 0;
        }

        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            context.getSource().sendFailure(Component.translatable("commands.sre.streaming_spectator.not_active"));
            return 0;
        }

        restoreOriginalRole(player, session);
        PacketTracker.sendToClient(player, StreamingSpectatorPayload.stop());
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.sre.streaming_spectator.stopped"), false);
        return 1;
    }

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }

        List<UUID> stopped = new ArrayList<>();
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                stopped.add(entry.getKey());
                continue;
            }

            Session session = entry.getValue();
            SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(player.level());
            if (!gameComponent.isRunning()) {
                restoreOriginalRole(player, session);
                PacketTracker.sendToClient(player, StreamingSpectatorPayload.stop());
                stopped.add(entry.getKey());
                continue;
            }

            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                player.setGameMode(GameType.SPECTATOR);
            }

            ServerPlayer target = session.targetUuid == null
                    ? null
                    : server.getPlayerList().getPlayer(session.targetUuid);
            if (target == null || !isValidTarget(player, target)) {
                attachToRandomTarget(player, session, session.targetUuid);
                continue;
            }

            SRERole targetRole = gameComponent.getRole(target);
            if (targetRole == null) {
                attachToRandomTarget(player, session, session.targetUuid);
                continue;
            }
            SRERole currentRole = gameComponent.getRole(player);
            if (!sameRole(currentRole, targetRole)) {
                changeRoleQuietly(player, targetRole);
            }
            if (--session.inventorySyncCooldown <= 0) {
                sendWatchPayload(player, session, target);
            }
        }

        for (UUID uuid : stopped) {
            SESSIONS.remove(uuid);
        }
    }

    private static boolean attachToRandomTarget(ServerPlayer player, Session session, UUID excludedTarget) {
        List<ServerPlayer> candidates = findCandidates(player, excludedTarget);
        if (candidates.isEmpty() && excludedTarget != null) {
            candidates = findCandidates(player, null);
        }

        if (candidates.isEmpty()) {
            session.targetUuid = null;
            session.inventorySyncCooldown = 0;
            if (!session.waitingForTarget) {
                restoreOriginalRole(player, session);
                PacketTracker.sendToClient(player, StreamingSpectatorPayload.waiting());
                player.displayClientMessage(Component.translatable("commands.sre.streaming_spectator.waiting"), true);
                session.waitingForTarget = true;
            }
            return false;
        }

        ServerPlayer target = candidates.get(RANDOM.nextInt(candidates.size()));
        SRERole targetRole = SREGameWorldComponent.KEY.get(player.level()).getRole(target);
        if (targetRole == null) {
            return false;
        }

        session.targetUuid = target.getUUID();
        session.cameraMode = RANDOM.nextBoolean()
                ? StreamingSpectatorPayload.CAMERA_FIRST_PERSON
                : StreamingSpectatorPayload.CAMERA_THIRD_PERSON_BACK;
        session.waitingForTarget = false;
        changeRoleQuietly(player, targetRole);
        sendWatchPayload(player, session, target);
        player.displayClientMessage(Component.translatable(
                "commands.sre.streaming_spectator.target", target.getGameProfile().getName()), true);
        return true;
    }

    private static void sendWatchPayload(ServerPlayer player, Session session, ServerPlayer target) {
        PacketTracker.sendToClient(player,
                StreamingSpectatorPayload.watch(session.targetUuid, session.cameraMode, hotbarSnapshot(target),
                        target.getInventory().selected));
        session.inventorySyncCooldown = INVENTORY_SYNC_INTERVAL_TICKS;
    }

    private static List<ItemStack> hotbarSnapshot(ServerPlayer target) {
        List<ItemStack> stacks = new ArrayList<>(StreamingSpectatorPayload.HOTBAR_SLOTS);
        for (int i = 0; i < StreamingSpectatorPayload.HOTBAR_SLOTS; i++) {
            stacks.add(target.getInventory().items.get(i).copy());
        }
        return stacks;
    }

    private static List<ServerPlayer> findCandidates(ServerPlayer player, UUID excludedTarget) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer target : player.server.getPlayerList().getPlayers()) {
            if (excludedTarget != null && excludedTarget.equals(target.getUUID())) {
                continue;
            }
            if (isValidTarget(player, target)) {
                candidates.add(target);
            }
        }
        return candidates;
    }

    private static boolean isValidTarget(ServerPlayer viewer, ServerPlayer target) {
        if (viewer.getUUID().equals(target.getUUID())) {
            return false;
        }
        if (viewer.level() != target.level()) {
            return false;
        }
        if (target.isCreative() || target.isSpectator()) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        if (SREPlayerAFKComponent.KEY.get(target).isAFK()) {
            return false;
        }
        return SREGameWorldComponent.KEY.get(target.level()).getRole(target) != null;
    }

    private static void changeRoleQuietly(ServerPlayer player, SRERole role) {
        if (role == null) {
            return;
        }
        RoleUtils.changeRole(player, role, false, false, false);
    }

    private static void restoreOriginalRole(ServerPlayer player, Session session) {
        if (session.originalRole == null) {
            SREGameWorldComponent.KEY.get(player.level()).removeRole(player);
            SREGameWorldComponent.KEY.get(player.level()).syncRoles();
            return;
        }
        if (!sameRole(SREGameWorldComponent.KEY.get(player.level()).getRole(player), session.originalRole)) {
            RoleUtils.changeRole(player, session.originalRole, false, false, false);
        }
    }

    private static void restoreOriginalGameMode(ServerPlayer player, Session session) {
        if (session.originalGameType != null
                && player.gameMode.getGameModeForPlayer() != session.originalGameType) {
            player.setGameMode(session.originalGameType);
        }
    }

    private static boolean sameRole(SRERole first, SRERole second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return first.identifier().equals(second.identifier());
    }

    private static final class Session {
        private final SRERole originalRole;
        private final GameType originalGameType;
        private UUID targetUuid;
        private int cameraMode = StreamingSpectatorPayload.CAMERA_FIRST_PERSON;
        private boolean waitingForTarget;
        private int inventorySyncCooldown;

        private Session(SRERole originalRole, GameType originalGameType) {
            this.originalRole = originalRole;
            this.originalGameType = originalGameType;
        }
    }
}

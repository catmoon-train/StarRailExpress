/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.agmas.noellesroles.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SRERoleDataPlayerComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameInitialized;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.api.time.TimeRewind;
import org.agmas.noellesroles.api.time.TimeRewindAreaResult;
import org.agmas.noellesroles.api.time.TimeRewindAreaSnapshot;
import org.agmas.noellesroles.api.time.TimeRewindResult;
import org.agmas.noellesroles.api.time.TimeRewindSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Operator-only capture, playback and verification controls for time rewind.
 *
 * <p>
 * Snapshots are keyed by a {@link ResourceLocation} id so one player can hold
 * several named rewind points. Sub-commands follow lowercase + underscore
 * naming: {@code area_capture}, {@code clear_all} ...
 */
public final class TimeRewindCommand {
    private static final int DEFAULT_TICKS = 50;
    private static final ResourceLocation DEFAULT_ID = Noellesroles.id("default");
    private static final Map<UUID, Map<ResourceLocation, TimeRewindSnapshot>> PLAYER_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<ResourceLocation, TimeRewindAreaSnapshot>> AREA_SNAPSHOTS = new ConcurrentHashMap<>();

    private TimeRewindCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
        OnGameEnd.EVENT.register((a, b) -> {
            PLAYER_SNAPSHOTS.clear();
            AREA_SNAPSHOTS.clear();
        });
        OnGameInitialized.EVENT.register((a) -> {
            PLAYER_SNAPSHOTS.clear();
            AREA_SNAPSHOTS.clear();
        });
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "sre:rewind");
        register(dispatcher, "rewind");
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String root) {
        dispatcher.register(Commands.literal(root)
                .requires(source -> source.hasPermission(SREConfig.instance().timeRewindPermission))
                .executes(TimeRewindCommand::help)
                .then(Commands.literal("capture")
                        .executes(context -> capture(context, DEFAULT_ID,
                                List.of(context.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> capture(context, DEFAULT_ID,
                                        EntityArgument.getPlayers(context, "targets"))))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(context -> capture(context,
                                        ResourceLocationArgument.getId(context, "id"),
                                        List.of(context.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> capture(context,
                                                ResourceLocationArgument.getId(context, "id"),
                                                EntityArgument.getPlayers(context, "targets"))))))
                .then(Commands.literal("restore")
                        .executes(context -> restore(context, DEFAULT_ID,
                                List.of(context.getSource().getPlayerOrException()), null))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> restore(context, DEFAULT_ID,
                                        EntityArgument.getPlayers(context, "targets"), null))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 600))
                                        .executes(context -> restore(context, DEFAULT_ID,
                                                EntityArgument.getPlayers(context, "targets"),
                                                IntegerArgumentType.getInteger(context, "ticks")))))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(context -> restore(context,
                                        ResourceLocationArgument.getId(context, "id"),
                                        List.of(context.getSource().getPlayerOrException()), null))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> restore(context,
                                                ResourceLocationArgument.getId(context, "id"),
                                                EntityArgument.getPlayers(context, "targets"), null))
                                        .then(Commands.argument("ticks",
                                                IntegerArgumentType.integer(1, 600))
                                                .executes(context -> restore(context,
                                                        ResourceLocationArgument.getId(context, "id"),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        IntegerArgumentType.getInteger(context,
                                                                "ticks")))))))
                .then(Commands.literal("cancel")
                        .executes(context -> cancel(context, List.of(
                                context.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> cancel(context,
                                        EntityArgument.getPlayers(context, "targets")))))
                .then(Commands.literal("visual")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> visual(context,
                                        EntityArgument.getPlayers(context, "targets"), DEFAULT_TICKS))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1200))
                                        .executes(context -> visual(context,
                                                EntityArgument.getPlayers(context, "targets"),
                                                IntegerArgumentType.getInteger(context, "ticks"))))))
                .then(Commands.literal("area_capture")
                        .executes(context -> areaCapture(context, DEFAULT_ID))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(context -> areaCapture(context,
                                        ResourceLocationArgument.getId(context, "id")))))
                .then(Commands.literal("area_restore")
                        .executes(context -> areaRestore(context, DEFAULT_ID))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(context -> areaRestore(context,
                                        ResourceLocationArgument.getId(context, "id")))))
                .then(Commands.literal("roledata")
                        .executes(context -> roleData(context, DEFAULT_ID,
                                List.of(context.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> roleData(context, DEFAULT_ID,
                                        EntityArgument.getPlayers(context, "targets"))))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(context -> roleData(context,
                                        ResourceLocationArgument.getId(context, "id"),
                                        List.of(context.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> roleData(context,
                                                ResourceLocationArgument.getId(context, "id"),
                                                EntityArgument.getPlayers(context, "targets"))))))
                .then(Commands.literal("status").executes(TimeRewindCommand::status))
                .then(Commands.literal("clear_all").executes(TimeRewindCommand::clearAll))
                .then(Commands.literal("clear_player")
                        .executes(context -> clearPlayer(context,
                                List.of(context.getSource().getPlayerOrException()), null))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> clearPlayer(context,
                                        EntityArgument.getPlayers(context, "targets"), null)))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(context -> clearPlayer(context,
                                        List.of(context.getSource().getPlayerOrException()),
                                        ResourceLocationArgument.getId(context, "id")))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> clearPlayer(context,
                                                EntityArgument.getPlayers(context, "targets"),
                                                ResourceLocationArgument.getId(context, "id"))))))
                .then(Commands.literal("clear_area")
                        .executes(context -> clearArea(context, null))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(context -> clearArea(context,
                                        ResourceLocationArgument.getId(context, "id"))))));
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.translatable("sre.command.rewind.help")
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int capture(CommandContext<CommandSourceStack> context,
            ResourceLocation id, Collection<ServerPlayer> targets) {
        int captured = 0;
        int warnings = 0;
        for (ServerPlayer player : targets) {
            TimeRewindSnapshot snapshot = TimeRewind.capture(player);
            PLAYER_SNAPSHOTS.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                    .put(id, snapshot);
            warnings += snapshot.warnings().size();
            captured++;
        }
        int finalCaptured = captured;
        int finalWarnings = warnings;
        context.getSource().sendSuccess(() -> Component.translatable(
                "sre.command.rewind.capture.success", finalCaptured, id.toString(), finalWarnings)
                .withStyle(ChatFormatting.AQUA), true);
        return captured;
    }

    /**
     * Restores the stored snapshot. Without {@code ticks} the snapshot is
     * applied directly (instant, no animation); with {@code ticks} it becomes a
     * smooth rewind that queues per player.
     */
    private static int restore(CommandContext<CommandSourceStack> context,
            ResourceLocation id, Collection<ServerPlayer> targets, Integer ticks) {
        int done = 0;
        for (ServerPlayer player : targets) {
            Map<ResourceLocation, TimeRewindSnapshot> snapshots = PLAYER_SNAPSHOTS.get(player.getUUID());
            TimeRewindSnapshot snapshot = snapshots == null ? null : snapshots.get(id);
            if (snapshot == null) {
                context.getSource().sendFailure(Component.translatable(
                        "sre.command.rewind.restore.not_found",
                        player.getScoreboardName(), id.toString()));
                continue;
            }
            if (ticks == null) {
                TimeRewindResult result = TimeRewind.restore(player, snapshot);
                if (result.isSuccess()) {
                    done++;
                }
                ChatFormatting color = result.isSuccess() ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
                context.getSource().sendSuccess(() -> Component.translatable(
                        "sre.command.rewind.restore.direct_complete", id.toString(),
                        player.getScoreboardName(), result.restoredComponents(),
                        result.failures().size()).withStyle(color), true);
                continue;
            }
            if (TimeRewind.smoothRestore(player, snapshot, ticks, result -> {
                ChatFormatting color = result.isSuccess() ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
                context.getSource().sendSuccess(() -> Component.translatable(
                        "sre.command.rewind.restore.smooth_complete", id.toString(),
                        player.getScoreboardName(), result.restoredComponents(),
                        result.failures().size()).withStyle(color), true);
            })) {
                done++;
            } else {
                context.getSource().sendFailure(Component.translatable(
                        "sre.command.rewind.restore.mismatch", player.getScoreboardName()));
            }
        }
        int finalDone = done;
        boolean smooth = ticks != null;
        var summary = smooth
                ? Component.translatable("sre.command.rewind.restore.summary_smooth",
                        finalDone, id.toString(), ticks)
                : Component.translatable("sre.command.rewind.restore.summary_direct",
                        finalDone, id.toString());
        context.getSource().sendSuccess(() -> summary.withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return done;
    }

    private static int cancel(CommandContext<CommandSourceStack> context,
            Collection<ServerPlayer> targets) {
        int cancelled = 0;
        for (ServerPlayer player : targets) {
            if (TimeRewind.cancelSmoothRestore(player)) {
                cancelled++;
            }
        }
        int finalCancelled = cancelled;
        context.getSource().sendSuccess(() -> Component.translatable(
                "sre.command.rewind.cancel.success", finalCancelled)
                .withStyle(ChatFormatting.YELLOW), true);
        return cancelled;
    }

    private static int visual(CommandContext<CommandSourceStack> context,
            Collection<ServerPlayer> targets, int ticks) {
        targets.forEach(player -> TimeRewind.playVisual(player, ticks));
        context.getSource().sendSuccess(() -> Component.translatable(
                "sre.command.rewind.visual.success", targets.size(), ticks)
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return targets.size();
    }

    private static int areaCapture(CommandContext<CommandSourceStack> context, ResourceLocation id) {
        ServerLevel level = context.getSource().getLevel();
        AABB area = AreasWorldComponent.KEY.get(level).getPlayArea();
        if (area == null) {
            context.getSource().sendFailure(Component.translatable(
                    "sre.command.rewind.area_capture.no_area"));
            return 0;
        }
        TimeRewindAreaSnapshot snapshot = TimeRewind.captureArea(level, area);
        AREA_SNAPSHOTS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>())
                .put(id, snapshot);
        context.getSource().sendSuccess(() -> Component.translatable(
                "sre.command.rewind.area_capture.success", id.toString(), snapshot.itemCount(),
                snapshot.doorCount(), snapshot.warnings().size())
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int areaRestore(CommandContext<CommandSourceStack> context, ResourceLocation id) {
        ServerLevel level = context.getSource().getLevel();
        Map<ResourceLocation, TimeRewindAreaSnapshot> snapshots = AREA_SNAPSHOTS.get(level.dimension());
        TimeRewindAreaSnapshot snapshot = snapshots == null ? null : snapshots.get(id);
        if (snapshot == null) {
            context.getSource().sendFailure(Component.translatable(
                    "sre.command.rewind.area_restore.not_found", id.toString()));
            return 0;
        }
        TimeRewindAreaResult result = TimeRewind.restoreArea(level, snapshot);
        ChatFormatting color = result.isSuccess() ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        context.getSource().sendSuccess(() -> Component.translatable(
                "sre.command.rewind.area_restore.result", result.restoredItems(),
                result.removedCurrentItems(), result.restoredDoors(), result.failures().size())
                .withStyle(color), true);
        return result.isSuccess() ? 1 : 0;
    }

    private static int roleData(CommandContext<CommandSourceStack> context,
            ResourceLocation id, Collection<ServerPlayer> targets) {
        int found = 0;
        for (ServerPlayer player : targets) {
            Map<ResourceLocation, TimeRewindSnapshot> snapshots = PLAYER_SNAPSHOTS.get(player.getUUID());
            TimeRewindSnapshot snapshot = snapshots == null ? null : snapshots.get(id);
            boolean included = snapshot != null
                    && snapshot.containsComponent(SRERoleDataPlayerComponent.KEY.getId());
            var current = SRERoleDataPlayerComponent.KEY.get(player).roleData;
            String roleDataClass = current == null ? "<none>" : current.getClass().getSimpleName();
            ChatFormatting color = included ? ChatFormatting.GREEN : ChatFormatting.RED;
            context.getSource().sendSuccess(() -> Component.translatable(
                    "sre.command.rewind.roledata.entry", player.getScoreboardName(),
                    roleDataClass, included, id.toString()).withStyle(color), false);
            if (included) {
                found++;
            }
        }
        return found;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        int playerEntries = PLAYER_SNAPSHOTS.values().stream().mapToInt(Map::size).sum();
        int areaEntries = AREA_SNAPSHOTS.values().stream().mapToInt(Map::size).sum();
        context.getSource().sendSuccess(() -> Component.translatable(
                "sre.command.rewind.status", playerEntries, areaEntries,
                TimeRewind.activeSmoothRewinds()).withStyle(ChatFormatting.AQUA), false);
        return playerEntries;
    }

    /** Clears every stored player and area snapshot on the server. */
    private static int clearAll(CommandContext<CommandSourceStack> context) {
        int playerEntries = PLAYER_SNAPSHOTS.values().stream().mapToInt(Map::size).sum();
        int areaEntries = AREA_SNAPSHOTS.values().stream().mapToInt(Map::size).sum();
        PLAYER_SNAPSHOTS.clear();
        AREA_SNAPSHOTS.clear();
        int count = playerEntries + areaEntries;
        context.getSource().sendSuccess(() -> Component.translatable(
                "sre.command.rewind.clear_all.success", count, playerEntries, areaEntries)
                .withStyle(ChatFormatting.YELLOW), true);
        return count;
    }

    /** Clears every snapshot of the given players, or only one id when given. */
    private static int clearPlayer(CommandContext<CommandSourceStack> context,
            Collection<ServerPlayer> targets, ResourceLocation id) {
        int cleared = 0;
        for (ServerPlayer player : targets) {
            Map<ResourceLocation, TimeRewindSnapshot> snapshots = PLAYER_SNAPSHOTS.get(player.getUUID());
            if (snapshots == null || snapshots.isEmpty()) {
                continue;
            }
            if (id == null) {
                cleared += snapshots.size();
                PLAYER_SNAPSHOTS.remove(player.getUUID());
            } else if (snapshots.remove(id) != null) {
                cleared++;
                if (snapshots.isEmpty()) {
                    PLAYER_SNAPSHOTS.remove(player.getUUID());
                }
            }
        }
        int finalCleared = cleared;
        var message = id == null
                ? Component.translatable("sre.command.rewind.clear_player.success", finalCleared)
                : Component.translatable("sre.command.rewind.clear_player.success_with_id",
                        finalCleared, id.toString());
        context.getSource().sendSuccess(() -> message.withStyle(ChatFormatting.YELLOW), true);
        return cleared;
    }

    /**
     * Clears every area snapshot in the current level, or only one id when given.
     */
    private static int clearArea(CommandContext<CommandSourceStack> context, ResourceLocation id) {
        ServerLevel level = context.getSource().getLevel();
        Map<ResourceLocation, TimeRewindAreaSnapshot> snapshots = AREA_SNAPSHOTS.get(level.dimension());
        int cleared = 0;
        if (snapshots != null && !snapshots.isEmpty()) {
            if (id == null) {
                cleared = snapshots.size();
                AREA_SNAPSHOTS.remove(level.dimension());
            } else if (snapshots.remove(id) != null) {
                cleared = 1;
                if (snapshots.isEmpty()) {
                    AREA_SNAPSHOTS.remove(level.dimension());
                }
            }
        }
        int finalCleared = cleared;
        var message = id == null
                ? Component.translatable("sre.command.rewind.clear_area.success", finalCleared)
                : Component.translatable("sre.command.rewind.clear_area.success_with_id",
                        finalCleared, id.toString());
        context.getSource().sendSuccess(() -> message.withStyle(ChatFormatting.YELLOW), true);
        return cleared;
    }
}

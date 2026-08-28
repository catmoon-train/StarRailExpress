package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.packet.FakeSteveApparitionS2CPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server state for target-only, non-entity apparitions. */
public final class FakeSteveApparitions {
    private static final int SCAN_INTERVAL = 5 * 20;
    private static final int RELOCATE_TICKS = 30 * 20;
    private static final Map<UUID, Apparition> ACTIVE = new HashMap<>();

    private FakeSteveApparitions() {
    }

    static boolean spawnFor(ServerPlayer target, boolean commanded) {
        if (ACTIVE.containsKey(target.getUUID()) || FakeSteveDirector.isReplaced(target)) {
            return false;
        }
        Vec3 position = findPosition(target);
        if (position == null) {
            return false;
        }
        UUID id = UUID.randomUUID();
        ACTIVE.put(target.getUUID(), new Apparition(id, position, commanded,
                target.serverLevel().getGameTime()));
        ServerPlayNetworking.send(target,
                new FakeSteveApparitionS2CPacket(id, position.x, position.y, position.z, false));
        return true;
    }

    static void tick(ServerLevel level, boolean hasPendingEvent) {
        long now = level.getGameTime();
        for (UUID targetId : List.copyOf(ACTIVE.keySet())) {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(targetId);
            Apparition apparition = ACTIVE.get(targetId);
            if (apparition == null || target == null || target.serverLevel() != level
                    || !GameUtils.isPlayerAliveAndSurvival(target)) {
                ACTIVE.remove(targetId);
                if (target != null) {
                    sendRemove(target, apparition);
                }
                if (apparition != null && !apparition.commanded) {
                    FakeSteveDirector.requeuePendingEvent(level);
                } else if (apparition != null) {
                    notifyAdministrators(level, "command.noellesroles.fake_steve.spawn_cancelled");
                }
                continue;
            }
            if (now - apparition.spawnTick >= RELOCATE_TICKS) {
                sendRemove(target, apparition);
                ACTIVE.remove(targetId);
                if (!spawnFor(target, apparition.commanded) && apparition.commanded) {
                    notifyAdministrators(level, "command.noellesroles.fake_steve.spawn_cancelled");
                }
            }
        }

        if (hasPendingEvent && now % SCAN_INTERVAL == 0L) {
            ServerPlayer selected = selectRiskTarget(level);
            if (selected != null && spawnFor(selected, false)) {
                FakeSteveDirector.consumePendingEvent(level);
            } else if (selected == null && now % (30 * 20L) == 0L) {
                for (ServerPlayer administrator : level.getServer().getPlayerList().getPlayers()) {
                    if (administrator.hasPermissions(2)) {
                        administrator.sendSystemMessage(Component.translatable(
                                "command.noellesroles.fake_steve.waiting"));
                    }
                }
            }
        }
    }

    static void cancelAll(ServerLevel level) {
        for (UUID targetId : List.copyOf(ACTIVE.keySet())) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(targetId);
            Apparition apparition = ACTIVE.remove(targetId);
            if (player != null && player.serverLevel() == level) {
                sendRemove(player, apparition);
            }
        }
    }

    public static void onLost(ServerPlayer player, UUID apparitionId) {
        Apparition apparition = ACTIVE.get(player.getUUID());
        if (apparition == null || !apparition.id.equals(apparitionId)
                || !FakeSteveDirector.isActive(player.serverLevel())
                || !FakeSteveDirector.canGenerate(player.serverLevel())) {
            return;
        }
        ACTIVE.remove(player.getUUID());
        sendRemove(player, apparition);
        FakeSteveDirector.replace(player, ReplacementCause.APPARITION);
    }

    private static ServerPlayer selectRiskTarget(ServerLevel level) {
        List<Candidate> candidates = new ArrayList<>();
        int totalWeight = 0;
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player) || FakeSteveDirector.isReplaced(player)
                    || ACTIVE.containsKey(player.getUUID())) {
                continue;
            }
            SRERole role = SREGameWorldComponent.KEY.get(level).getRole(player);
            int sanity = role != null && role.getMoodType() == SRERole.MoodType.REAL
                    ? Mth.clamp(Math.round(SREPlayerMoodComponent.KEY.get(player).getMood() * 100.0F), 0, 100)
                    : 100;
            int bodies = level.getEntities(TMMEntities.PLAYER_BODY,
                    new AABB(player.blockPosition()).inflate(12.0D), body -> true).size();
            int weight = FakeSteveRules.apparitionRisk(sanity, bodies);
            if (weight > 0) {
                candidates.add(new Candidate(player, weight));
                totalWeight += weight;
            }
        }
        if (totalWeight <= 0) {
            return null;
        }
        int roll = level.getRandom().nextInt(totalWeight);
        for (Candidate candidate : candidates) {
            roll -= candidate.weight;
            if (roll < 0) {
                return candidate.player;
            }
        }
        return candidates.getLast().player;
    }

    private static Vec3 findPosition(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = 6.0D + level.getRandom().nextDouble() * 4.0D;
            int x = Mth.floor(target.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(target.getZ() + Math.sin(angle) * distance);
            for (int yOffset = 2; yOffset >= -3; yOffset--) {
                BlockPos feet = new BlockPos(x, Mth.floor(target.getY()) + yOffset, z);
                if (level.getBlockState(feet).isAir()
                        && level.getBlockState(feet.above()).isAir()
                        && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(),
                                net.minecraft.core.Direction.UP)) {
                    return Vec3.atBottomCenterOf(feet);
                }
            }
        }
        return null;
    }

    private static void sendRemove(ServerPlayer target, Apparition apparition) {
        if (apparition != null) {
            ServerPlayNetworking.send(target,
                    new FakeSteveApparitionS2CPacket(apparition.id, 0, 0, 0, true));
        }
    }

    private static void notifyAdministrators(ServerLevel level, String translationKey) {
        for (ServerPlayer administrator : level.getServer().getPlayerList().getPlayers()) {
            if (administrator.hasPermissions(2)) {
                administrator.sendSystemMessage(Component.translatable(translationKey));
            }
        }
    }

    private record Candidate(ServerPlayer player, int weight) {
    }

    private record Apparition(UUID id, Vec3 position, boolean commanded, long spawnTick) {
    }
}

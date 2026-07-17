package net.exmo.sre.sixtyseconds.content.entity;

import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.exmo.sre.sixtyseconds.network.CreateClientMarkingAreaPacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 标记区域管理器（服务端）
 * 负责对范围内玩家施加发光效果，并同步粒子区域到客户端
 */
public class ServerMarkingAreaManager {
    private static final List<MarkingArea> activeAreas = new ArrayList<>();
    private static final int GLOWING_DURATION = 60; // 3秒

    public static void createMarkingArea(ServerLevel world, Vec3 position, double radius, int durationTicks) {
        for (ServerPlayer player : world.players()) {
            ServerPlayNetworking.send(player, new CreateClientMarkingAreaPacket(position, radius, durationTicks));
        }
        activeAreas.add(new MarkingArea(world, position, radius, durationTicks));
    }

    public static void tick() {
        Iterator<MarkingArea> iterator = activeAreas.iterator();
        while (iterator.hasNext()) {
            MarkingArea area = iterator.next();
            if (area.tick()) {
                iterator.remove();
            }
        }
    }

    public static void clearAll() {
        activeAreas.clear();
    }

    private static class MarkingArea {
        private final ServerLevel world;
        private final Vec3 center;
        private final double radius;
        private int remainingTicks;
        private int tickCounter = 0;

        public MarkingArea(ServerLevel world, Vec3 center, double radius, int durationTicks) {
            this.world = world;
            this.center = center;
            this.radius = radius;
            this.remainingTicks = durationTicks;
        }

        public boolean tick() {
            remainingTicks--;
            tickCounter++;
            if (remainingTicks <= 0) return true;

            if (tickCounter % 20 == 1) {
                applyGlowingToPlayers();
            }
            return false;
        }

        private void applyGlowingToPlayers() {
            AABB area = new AABB(
                    center.x - radius, center.y - 1, center.z - radius,
                    center.x + radius, center.y + 4, center.z + radius);
            List<ServerPlayer> players = world.getEntitiesOfClass(
                    ServerPlayer.class, area, GameUtils::isPlayerAliveAndSurvival);
            for (ServerPlayer player : players) {
                double distance = player.position().distanceTo(center);
                if (distance <= radius) {
                    player.addEffect(new MobEffectInstance(
                            MobEffects.GLOWING, GLOWING_DURATION, 0, false, false));
                }
            }
        }
    }
}

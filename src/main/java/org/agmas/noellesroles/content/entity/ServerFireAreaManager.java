package org.agmas.noellesroles.content.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** 燃烧区域管理器 — 燃烧弹/燃烧瓶落点持续火焰效果 */
public class ServerFireAreaManager {
    private static final List<FireArea> activeAreas = new ArrayList<>();

    public static void createFireArea(ServerLevel world, Vec3 position, double radius, int durationTicks, boolean damageMobs) {
        activeAreas.add(new FireArea(world, position, radius, durationTicks, damageMobs));
    }

    public static void tick() {
        Iterator<FireArea> it = activeAreas.iterator();
        while (it.hasNext()) {
            if (it.next().tick()) it.remove();
        }
    }

    public static void clearAll() {
        activeAreas.clear();
    }

    private static class FireArea {
        private final ServerLevel world;
        private final Vec3 center;
        private final double radius;
        private final boolean damageMobs;
        private int remainingTicks;
        private int tickCounter = 0;

        public FireArea(ServerLevel world, Vec3 center, double radius, int durationTicks, boolean damageMobs) {
            this.world = world;
            this.center = center;
            this.radius = radius;
            this.remainingTicks = durationTicks;
            this.damageMobs = damageMobs;
        }

        public boolean tick() {
            remainingTicks--;
            tickCounter++;
            if (remainingTicks <= 0) return true;

            if (tickCounter % 2 == 0) spawnFireParticles();
            if (tickCounter % 10 == 0) applyFire();

            return false;
        }

        private void spawnFireParticles() {
            for (int i = 0; i < 6; i++) {
                double ox = (world.random.nextDouble() - 0.5) * radius * 2;
                double oz = (world.random.nextDouble() - 0.5) * radius * 2;
                double oy = world.random.nextDouble() * 0.5;
                world.sendParticles(ParticleTypes.FLAME,
                        center.x + ox, center.y + oy, center.z + oz,
                        1, 0.05, 0.1, 0.05, 0.02);
            }
        }

        private void applyFire() {
            AABB area = new AABB(
                    center.x - radius, center.y - 1, center.z - radius,
                    center.x + radius, center.y + 3, center.z + radius);
            for (LivingEntity entity : world.getEntitiesOfClass(LivingEntity.class, area, e -> {
                if (e instanceof ServerPlayer p) return !p.isCreative() && !p.isSpectator();
                return damageMobs && e instanceof Mob;
            })) {
                if (entity.position().distanceTo(center) <= radius) {
                    entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), 40));
                }
            }
        }
    }
}

package net.exmo.sre.sixtyseconds.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 标记区域管理器（客户端）
 * 严格参照 ClientSmokeAreaManager 结构，使用白色粒子渲染标记弹烟雾
 */
@Environment(EnvType.CLIENT)
public class ClientMarkingAreaManager {
    private static final List<MarkingArea> activeAreas = new ArrayList<>();

    public static void createMarkingArea(ClientLevel world, Vec3 position, double radius, int durationTicks) {
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
        private final ClientLevel world;
        private final Vec3 center;
        private final double radius;
        private int remainingTicks;
        private int tickCounter = 0;

        public MarkingArea(ClientLevel world, Vec3 center, double radius, int durationTicks) {
            this.world = world;
            this.center = center;
            this.radius = radius;
            this.remainingTicks = durationTicks;
        }

        final int DISPLAY_LIMIT = 24;

        public boolean tick() {
            remainingTicks--;
            tickCounter++;
            if (remainingTicks <= 0) return true;

            // 每 3 tick 生成粒子（与 ClientSmokeAreaManager 一致）
            if (tickCounter % 3 == 0) {
                spawnMarkingParticles();
            }
            return false;
        }

        private void spawnMarkingParticles() {
            var client = Minecraft.getInstance();
            if (client.player == null) return;
            if (center.distanceToSqr(client.player.position()) >= DISPLAY_LIMIT * DISPLAY_LIMIT) return;

            for (int i = 0; i < 250; i++) {
                double offsetX = (world.random.nextDouble() - 0.5) * radius * 2;
                double offsetY = -1d + world.random.nextDouble() * 4.5;
                double offsetZ = (world.random.nextDouble() - 0.5) * radius * 2;
                int motionX = world.random.nextBoolean() ? -1 : 1;
                int motionY = world.random.nextBoolean() ? -1 : 1;
                int motionZ = world.random.nextBoolean() ? -1 : 1;

                // 主要白色烟雾粒子
                world.addAlwaysVisibleParticle(ParticleTypes.WHITE_SMOKE, true,
                        center.x + offsetX, center.y + offsetY, center.z + offsetZ,
                        0.1 * motionX, 0.1 * motionY, 0.1 * motionZ);

                // 额外添加白色粒子变体
                if (i % 3 == 0) {
                    world.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true,
                            center.x + offsetX, center.y + offsetY, center.z + offsetZ,
                            0.15 * motionX, 0.15 * motionY, 0.15 * motionZ);
                }
                if (i % 5 == 0) {
                    world.addAlwaysVisibleParticle(ParticleTypes.EFFECT, true,
                            center.x + offsetX, center.y + offsetY, center.z + offsetZ,
                            0.12 * motionX, 0.12 * motionY, 0.12 * motionZ);
                }
            }
        }
    }
}

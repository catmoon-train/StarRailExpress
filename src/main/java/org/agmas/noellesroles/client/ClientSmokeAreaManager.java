package org.agmas.noellesroles.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * 烟雾区域管理器
 * 用于管理烟雾弹产生的持续烟雾效果区域
 */
@Environment(EnvType.CLIENT)
public class ClientSmokeAreaManager {

    // 所有活跃的烟雾区域
    private static final List<SmokeArea> activeAreas = new ArrayList<>();

    /**
     * 创建一个新的烟雾区域
     */
    public static void createSmokeArea(ClientLevel world, Vec3 position, double radius, int durationTicks) {
        activeAreas.add(new SmokeArea(world, position, radius, durationTicks));
    }

    /**
     * 每tick更新所有烟雾区域
     * 应该在游戏循环中调用
     */
    public static void tick() {
        Iterator<SmokeArea> iterator = activeAreas.iterator();
        while (iterator.hasNext()) {
            SmokeArea area = iterator.next();
            if (area.tick()) {
                // 区域已过期，移除
                iterator.remove();
            }
        }
    }

    /**
     * 清除所有烟雾区域（游戏结束时调用）
     */
    public static void clearAll() {
        activeAreas.clear();
    }

    /**
     * 烟雾区域数据类
     */
    private static class SmokeArea {
        private final ClientLevel world;
        private final Vec3 center;
        private final double radius;
        private int remainingTicks;
        private int tickCounter = 0;

        public SmokeArea(ClientLevel world, Vec3 center, double radius, int durationTicks) {
            this.world = world;
            this.center = center;
            this.radius = radius;
            this.remainingTicks = durationTicks;
        }

        /**
         * 每tick更新
         * 
         * @return true 如果区域已过期
         */
        public boolean tick() {
            remainingTicks--;
            tickCounter++;

            if (remainingTicks <= 0) {
                return true;
            }

            // 每3tick生成粒子效果（更频繁）
            if (tickCounter % 3 == 0) {
                spawnSmokeParticles();
            }

            return false;
        }

        /**
         * 生成烟雾粒子
         */
        private void spawnSmokeParticles() {
            // 大幅增加粒子数量以获得超浓密的烟雾效果（从25增加到250，10倍）
            for (int i = 0; i < 250; i++) {
                double offsetX = (world.random.nextDouble() - 0.5) * radius * 2;
                double offsetY = -1d + world.random.nextDouble() * 4.5; // 增加高度范围
                double offsetZ = (world.random.nextDouble() - 0.5) * radius * 2;

                // 主要烟雾粒子（增加数量）
                // ParticleEffect parameters, boolean alwaysSpawn, double x, double y, double z,
                // double velocityX, double velocityY, double velocityZ
                world.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, center.x + offsetX,
                        center.y + offsetY, center.z + offsetZ, 0.1, 0.1, 0.1);

                // 大量添加大型烟雾粒子
                if (i % 3 == 0) {
                    world.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, center.x + offsetX,
                            center.y + offsetY, center.z + offsetZ, 0.15, 0.15, 0.15);
                }

                // 添加普通烟雾粒子
                if (i % 5 == 0) {
                    world.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, center.x + offsetX, center.y + offsetY,
                            center.z + offsetZ, 0.12, 0.12, 0.12);
                }
            }
        }
    }
}
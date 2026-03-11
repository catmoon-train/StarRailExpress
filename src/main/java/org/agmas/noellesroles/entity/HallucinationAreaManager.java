package org.agmas.noellesroles.entity;

import io.wifi.starrailexpress.game.GameFunctions;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 迷幻区域管理器
 * 用于管理迷幻瓶产生的持续迷幻效果区域
 * - 20格范围内玩家视野会随机偏离视角
 * - 持续时间：3秒
 * - 触发间隔：1秒
 */
public class HallucinationAreaManager {
    
    // 所有活跃的迷幻区域
    private static final List<HallucinationArea> activeAreas = new ArrayList<>();
    
    /**
     * 创建一个新的迷幻区域
     */
    public static void createHallucinationArea(ServerLevel world, Vec3 position, double radius, 
                                                int durationTicks, int triggerIntervalTicks, Player owner) {
        activeAreas.add(new HallucinationArea(world, position, radius, durationTicks, triggerIntervalTicks, owner));
    }
    
    /**
     * 每tick更新所有迷幻区域
     * 应该在游戏循环中调用
     */
    public static void tick() {
        Iterator<HallucinationArea> iterator = activeAreas.iterator();
        while (iterator.hasNext()) {
            HallucinationArea area = iterator.next();
            if (area.tick()) {
                // 区域已过期，移除
                iterator.remove();
            }
        }
    }
    
    /**
     * 清除所有迷幻区域（游戏结束时调用）
     */
    public static void clearAll() {
        activeAreas.clear();
    }
    
    /**
     * 对玩家应用迷幻效果（视角随机偏移 + 反胃效果）
     */
    public static void applyHallucinationToPlayer(ServerPlayer player) {
        if (player == null || !GameFunctions.isPlayerAliveAndSurvival(player)) {
            return;
        }
        
        // 生成随机视角偏移 - 幅度增加
        float yawOffset = (player.getRandom().nextFloat() - 0.5f) * 180f;  // -90 到 +90 度
        float pitchOffset = (player.getRandom().nextFloat() - 0.5f) * 120f;  // -60 到 +60 度
        
        // 直接修改玩家视角
        float newYaw = player.getYRot() + yawOffset;
        float newPitch = Math.max(-90f, Math.min(90f, player.getXRot() + pitchOffset));
        
        // 使用 teleport 同步视角到客户端
        player.connection.teleport(
                player.getX(), player.getY(), player.getZ(),
                newYaw, newPitch
        );
        
        // 施加强化反胃效果来增强迷幻感
        if (!player.hasEffect(MobEffects.CONFUSION)) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.CONFUSION, 60, 1, false, false, false  // 持续时间增加到1.5秒，等级提升
            ));
        }
    }
    
    /**
     * 迷幻区域数据类
     */
    private static class HallucinationArea {
        private final ServerLevel world;
        private final Vec3 center;
        private final double radius;
        private final int triggerInterval;
        private final Player owner;
        private int remainingTicks;
        private int tickCounter = 0;
        
        public HallucinationArea(ServerLevel world, Vec3 center, double radius, 
                                 int durationTicks, int triggerIntervalTicks, Player owner) {
            this.world = world;
            this.center = center;
            this.radius = radius;
            this.remainingTicks = durationTicks;
            this.triggerInterval = triggerIntervalTicks;
            this.owner = owner;
        }
        
        /**
         * 每tick更新
         * @return true 如果区域已过期
         */
        public boolean tick() {
            remainingTicks--;
            tickCounter++;
            
            if (remainingTicks <= 0) {
                return true;
            }
            
            // 每3tick生成粒子效果
            if (tickCounter % 3 == 0) {
                spawnHallucinationParticles();
            }
            
            // 每隔触发间隔对范围内玩家应用迷幻效果
            if (tickCounter % triggerInterval == 0) {
                applyHallucinationToPlayersInRadius();
            }
            
            return false;
        }
        
        /**
         * 生成迷幻粒子效果
         */
        private void spawnHallucinationParticles() {

            for (int i = 0; i < 80; i++) {
                double offsetX = (world.random.nextDouble() - 0.5) * radius * 2;
                double offsetY = world.random.nextDouble() * 5;  // 高度范围
                double offsetZ = (world.random.nextDouble() - 0.5) * radius * 2;
                
                // 紫色龙息粒子 - 主要效果
                world.sendParticles(ParticleTypes.DRAGON_BREATH,
                        center.x + offsetX, center.y + offsetY, center.z + offsetZ,
                        1, 0.1, 0.1, 0.1, 0.02);
                
                // 烟雾粒子
                if (i % 2 == 0) {
                    world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            center.x + offsetX, center.y + offsetY, center.z + offsetZ,
                            2, 0.15, 0.15, 0.15, 0.03);
                }
                
                // 女巫粒子 - 迷幻效果
                if (i % 3 == 0) {  // 增加女巫粒子频率
                    world.sendParticles(ParticleTypes.WITCH,
                            center.x + offsetX, center.y + offsetY, center.z + offsetZ,
                            1, 0.1, 0.1, 0.1, 0.02);
                }
            }
        }
        
        /**
         * 对范围内玩家应用迷幻效果（视角偏移）
         */
        private void applyHallucinationToPlayersInRadius() {
            AABB area = new AABB(
                    center.x - radius, center.y - 5, center.z - radius,
                    center.x + radius, center.y + 10, center.z + radius
            );
            
            List<ServerPlayer> players = world.getEntitiesOfClass(
                    ServerPlayer.class, area,
                    player -> GameFunctions.isPlayerAliveAndSurvival(player) && player != owner
            );
            
            for (ServerPlayer player : players) {
                // 检查玩家是否在球形范围内
                double distance = player.position().distanceTo(center);
                if (distance <= radius) {
                    // 对区域内玩家多次应用效果以增加随机性
                    for (int i = 0; i < 3; i++) {
                        applyHallucinationToPlayer(player);
                    }
                }
            }
        }
    }
}
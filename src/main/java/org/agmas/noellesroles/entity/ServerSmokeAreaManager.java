package org.agmas.noellesroles.entity;

import io.wifi.starrailexpress.game.GameFunctions;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.agmas.noellesroles.packet.CreateClientSmokeAreaPacket;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 烟雾区域管理器
 * 用于管理烟雾弹产生的持续烟雾效果区域
 */
public class ServerSmokeAreaManager {
    
    // 所有活跃的烟雾区域
    private static final List<SmokeArea> activeAreas = new ArrayList<>();
    
    // 失明效果持续时间
    private static final int BLINDNESS_DURATION = 60; // 3秒
    
    /**
     * 创建一个新的烟雾区域
     */
    public static void createSmokeArea(ServerLevel world, Vec3 position, double radius, int durationTicks) {
        for (ServerPlayer player : world.players()) {
            ServerPlayNetworking.send(player, new CreateClientSmokeAreaPacket(position, radius, durationTicks));
        }
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
        private final ServerLevel world;
        private final Vec3 center;
        private final double radius;
        private int remainingTicks;
        private int tickCounter = 0;
        
        public SmokeArea(ServerLevel world, Vec3 center, double radius, int durationTicks) {
            this.world = world;
            this.center = center;
            this.radius = radius;
            this.remainingTicks = durationTicks;
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
            
            // // 每3tick生成粒子效果（更频繁）
            // if (tickCounter % 3 == 0) {
            //     spawnSmokeParticles();
            // }
            
            // 每20tick检查玩家并应用失明
            if (tickCounter % 20 == 1) {
                applyBlindnessToPlayers();
            }
            
            return false;
        }
        
        /**
         * 对范围内玩家应用失明效果
         */
        private void applyBlindnessToPlayers() {
            AABB area = new AABB(
                    center.x - radius, center.y - 1, center.z - radius,
                    center.x + radius, center.y + 4, center.z + radius
            );
            
            List<ServerPlayer> players = world.getEntitiesOfClass(
                    ServerPlayer.class, area,
                    GameFunctions::isPlayerAliveAndSurvival
            );
            
            for (ServerPlayer player : players) {
                // 检查玩家是否真的在球形范围内
                double distance = player.position().distanceTo(center);
                if (distance <= radius) {
                    player.addEffect(new MobEffectInstance(
                            MobEffects.BLINDNESS, BLINDNESS_DURATION, 0, false, false
                    ));
                }
            }
        }
    }
}
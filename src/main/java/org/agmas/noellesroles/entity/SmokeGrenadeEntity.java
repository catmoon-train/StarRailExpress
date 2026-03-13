package org.agmas.noellesroles.entity;


import io.wifi.starrailexpress.cca.StarPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import org.agmas.noellesroles.init.ModItems;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 烟雾弹实体
 * - 碰撞时爆炸形成烟雾区域
 * - 烟雾持续10秒
 * - 进入烟雾的玩家获得失明效果
 * - 直接命中玩家时清空目标的san值
 */
public class SmokeGrenadeEntity extends ThrowableItemProjectile {
    
    // 烟雾持续时间：10秒 = 200 ticks
    private static final int SMOKE_DURATION_TICKS = 200;
    // 烟雾半径
    private static final double SMOKE_RADIUS = 4.0;
    // 失明效果持续时间（比烟雾略长，确保连续性）
    private static final int BLINDNESS_DURATION = 70; // 2秒
    
    private boolean directHit = false;
    private Player directHitTarget = null;
    
    public SmokeGrenadeEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level world) {
        super(entityType, world);
    }
    
    @Override
    protected Item getDefaultItem() {
        return ModItems.SMOKE_GRENADE;
    }
    
    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        
        // 检查是否直接命中玩家
        if (entityHitResult.getEntity() instanceof Player player) {
            if (GameFunctions.isPlayerAliveAndSurvival(player)) {
                directHit = true;
                directHitTarget = player;
            }
        }
    }
    
    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        
        if (this.level() instanceof ServerLevel world) {
            // 播放烟雾爆炸音效
            world.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, 
                    SoundSource.PLAYERS, 1.5f, 0.5f);
            
            // 如果直接命中玩家，清空目标san值
            if (directHit && directHitTarget != null && directHitTarget instanceof ServerPlayer serverTarget) {
                // 给予目标额外的失明效果
                serverTarget.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
            }
            
            // 生成烟雾区域实体（使用粒子模拟）
            // 创建一个区域效果云或使用定时任务
            ServerSmokeAreaManager.createSmokeArea(world, this.position(), SMOKE_RADIUS, SMOKE_DURATION_TICKS);
            
            // 初始烟雾粒子爆发 - 大幅增强效果（10倍粒子）
            for (int i = 0; i < 150; i++) {
                double offsetX = (this.random.nextDouble() - 0.5) * SMOKE_RADIUS * 2;
                double offsetY = this.random.nextDouble() * 3;  // 增加高度范围
                double offsetZ = (this.random.nextDouble() - 0.5) * SMOKE_RADIUS * 2;
                
                // 主要烟雾粒子（增加数量）
                world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ,
                        3, 0.1, 0.1, 0.1, 0.03);
                        
                // 额外添加大量大型烟雾粒子
                if (i % 3 == 0) {
                    world.sendParticles(ParticleTypes.LARGE_SMOKE,
                            this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ,
                            2, 0.2, 0.2, 0.2, 0.05);
                }
                
                // 添加更多的烟雾效果粒子
                if (i % 5 == 0) {
                    world.sendParticles(ParticleTypes.SMOKE,
                            this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ,
                            1, 0.15, 0.15, 0.15, 0.04);
                }
            }
            
            // 立即对范围内玩家应用失明
            applyBlindnessToPlayersInRadius(world);
            
            this.discard();
        }
    }
    
    /**
     * 对范围内的玩家应用失明效果
     */

    private void applyBlindnessToPlayersInRadius(ServerLevel world) {
        AABB area = new AABB(
                this.getX() - SMOKE_RADIUS, this.getY() - 1, this.getZ() - SMOKE_RADIUS,
                this.getX() + SMOKE_RADIUS, this.getY() + 3, this.getZ() + SMOKE_RADIUS
        );
        
        List<ServerPlayer> players = world.getEntitiesOfClass(
                ServerPlayer.class, area,
                player -> GameFunctions.isPlayerAliveAndSurvival(player) && player != this.getOwner()
        );
        
        for (ServerPlayer player : players) {
            StarPlayerMoodComponent moodComponent = StarPlayerMoodComponent.KEY.get(player);
            // 设置san值为0（疯狂状态）
            moodComponent.setMood(0.25f);
            moodComponent.sync();
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, BLINDNESS_DURATION, 0, false, false));
        }
    }
}
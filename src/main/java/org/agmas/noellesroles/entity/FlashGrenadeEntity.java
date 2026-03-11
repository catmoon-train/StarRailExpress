package org.agmas.noellesroles.entity;

import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModItems;

import java.util.List;

/**
 * 闪光弹实体
 * - 落地时播放FIREWORK_LARGE_BLAST（烟花爆炸声）
 * - 检测玩家视野，让视野内有闪光弹的玩家获得试炼之兆效果（RAID_OMEN）3秒（60 ticks）
 * - 视野判定参考巡警实现：距离检查 + 视野角度检查 + 射线检测
 * - 视野角度：70度扇形（半角35度）
 */
public class FlashGrenadeEntity extends ThrowableItemProjectile {

    // 闪光半径：8格
    private static final double FLASH_RADIUS = 8.0;
    // 效果持续时间：3秒（60 ticks）
    private static final int EFFECT_DURATION = 60;

    public FlashGrenadeEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.FLASH_GRENADE;
    }

    @Override
    protected void onHit(net.minecraft.world.phys.HitResult hitResult) {
        super.onHit(hitResult);

        if (this.level() instanceof ServerLevel world) {
            // 播放声音 - FIREWORK_ROCKET_LARGE_BLAST（烟花爆炸声）
            world.playSound(null, this.blockPosition(),
                    SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.0f, 1.0f);

            // 检测玩家视野，让视野内有闪光弹的玩家获得试炼之兆效果
            applyFlashToPlayersWithLineOfSight(world);

            // 生成闪光粒子效果
            spawnFlashParticles(world);

            this.discard();
        }
    }

    /**
     * 检测玩家视野，让视野内有闪光弹的玩家给予试炼之兆效果（RAID_OMEN）3秒
     * 参考巡警的视野判定实现
     */
    private void applyFlashToPlayersWithLineOfSight(ServerLevel world) {
        // 在大范围内查找玩家（视野范围）
        AABB area = new AABB(
                this.getX() - FLASH_RADIUS, this.getY() - 2, this.getZ() - FLASH_RADIUS,
                this.getX() + FLASH_RADIUS, this.getY() + 4, this.getZ() + FLASH_RADIUS
        );

        List<ServerPlayer> players = world.getEntitiesOfClass(
                ServerPlayer.class, area,
                player -> GameFunctions.isPlayerAliveAndSurvival(player)
        );

        // 闪光弹位置（稍微偏上，考虑到玩家眼睛高度）
        Vec3 flashPos = this.position().add(0, 1.0, 0);

        for (ServerPlayer player : players) {
            // 检查玩家视线是否能看到闪光弹（参考巡警的视野判定）
            if (canSeeFlash(player, flashPos, world)) {
                // 给予试炼之兆效果（RAID_OMEN）3秒
                player.addEffect(new MobEffectInstance(
                        MobEffects.RAID_OMEN,
                        EFFECT_DURATION,
                        0, // 等级0
                        false, // 不显示粒子
                        false, // 不显示图标
                        false  // 不显示特效动画
                ));
            }
        }
    }

    /**
     * 检查玩家视线是否能看到闪光弹位置
     * 参考巡警的视野判定实现（PatrollerKillMixin.isBoundTargetVisible）
     */
    private boolean canSeeFlash(ServerPlayer player, Vec3 flashPos, ServerLevel world) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getViewVector(1.0f);

        // 检查距离
        double distance = eyePos.distanceTo(flashPos);
        if (distance > FLASH_RADIUS) {
            return false;
        }

        // 视野角度检查（参考巡警：使用点积判断）
        Vec3 toFlash = flashPos.subtract(eyePos).normalize();
        double dot = lookDir.dot(toFlash);
        // 60度扇形视野（半角30度）
        if (dot < Math.cos(Math.toRadians(30.0))) {
            return false;
        }

        // 射线检测（参考巡警：使用 ClipContext）
        net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
                eyePos, flashPos,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player);
        net.minecraft.world.phys.BlockHitResult hit = world.clip(context);
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ||
                hit.getLocation().distanceTo(flashPos) < 1.0;
    }

    /**
     * 生成闪光粒子效果
     */
    private void spawnFlashParticles(ServerLevel world) {
        // 使用末地烛白色粒子模拟闪光效果
        for (int i = 0; i < 100; i++) {
            double offsetX = (this.random.nextDouble() - 0.5) * FLASH_RADIUS * 2;
            double offsetY = this.random.nextDouble() * 3;
            double offsetZ = (this.random.nextDouble() - 0.5) * FLASH_RADIUS * 2;

            // 使用末地烛粒子
            world.sendParticles(ParticleTypes.END_ROD,
                    this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ,
                    1, 0.1, 0.1, 0.1, 0.03);
        }

        // 额外的白色烟雾粒子
        for (int i = 0; i < 50; i++) {
            double offsetX = (this.random.nextDouble() - 0.5) * FLASH_RADIUS * 1.5;
            double offsetY = this.random.nextDouble() * 2;
            double offsetZ = (this.random.nextDouble() - 0.5) * FLASH_RADIUS * 1.5;

            world.sendParticles(ParticleTypes.WHITE_SMOKE,
                    this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ,
                    1, 0.05, 0.05, 0.05, 0.02);
        }
    }
}

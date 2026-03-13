package org.agmas.noellesroles.entity;

import io.wifi.starrailexpress.cca.StarPlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.mixin.accessor.PlayerPoisonComponentAccessor;

import java.util.List;

/**
 * 净化弹实体
 * - 落地时播放GUARDIAN_ATTACK（守卫者激光射击声）
 * - 取消半径3格内玩家的中毒状态
 * - 粒子效果为dripping_water（水滴）
 */
public class PurifyBombEntity extends ThrowableItemProjectile {

    // 净化半径：3格
    private static final double PURIFY_RADIUS = 3.0;

    public PurifyBombEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.PURIFY_BOMB;
    }

    @Override
    protected void onHit(net.minecraft.world.phys.HitResult hitResult) {
        super.onHit(hitResult);

        if (this.level() instanceof ServerLevel world) {
            // 播放声音 - GUARDIAN_ATTACK（守卫者激光射击声）
            world.playSound(null, this.blockPosition(),
                    SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1.0f, 1.0f);

            // 取消半径3格内玩家的中毒状态
            purifyPlayersInRadius(world);

            // 生成气泡粒子效果
            spawnBubbleParticles(world);

            this.discard();
        }
    }

    /**
     * 取消半径3格内玩家的中毒状态
     */
    private void purifyPlayersInRadius(ServerLevel world) {
        AABB area = new AABB(
                this.getX() - PURIFY_RADIUS, this.getY() - 1, this.getZ() - PURIFY_RADIUS,
                this.getX() + PURIFY_RADIUS, this.getY() + 3, this.getZ() + PURIFY_RADIUS
        );

        List<ServerPlayer> players = world.getEntitiesOfClass(
                ServerPlayer.class, area,
                player -> GameFunctions.isPlayerAliveAndSurvival(player)
        );

        for (ServerPlayer player : players) {
            // 获取玩家的中毒组件
            StarPlayerPoisonComponent poisonComponent = StarPlayerPoisonComponent.KEY.get(player);

            // 如果玩家处于中毒状态，则清除中毒
            if (((PlayerPoisonComponentAccessor) poisonComponent).getPoisonTicks() > 0) {
                poisonComponent.setPoisonTicks(0, null);
                poisonComponent.sync();
            }
        }
    }

    /**
     * 生成水滴粒子效果
     */
    private void spawnBubbleParticles(ServerLevel world) {
        // 使用水滴粒子模拟净化效果
        for (int i = 0; i < 80; i++) {
            double offsetX = (this.random.nextDouble() - 0.5) * PURIFY_RADIUS * 2;
            double offsetY = this.random.nextDouble() * 3;
            double offsetZ = (this.random.nextDouble() - 0.5) * PURIFY_RADIUS * 2;

            // 使用水滴粒子
            world.sendParticles(ParticleTypes.DRIPPING_WATER,
                    this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ,
                    1, 0.1, 0.1, 0.1, 0.03);
        }

        // 额外的水滴下落效果
        for (int i = 0; i < 30; i++) {
            double offsetX = (this.random.nextDouble() - 0.5) * PURIFY_RADIUS;
            double offsetZ = (this.random.nextDouble() - 0.5) * PURIFY_RADIUS;

            world.sendParticles(ParticleTypes.DRIPPING_WATER,
                    this.getX() + offsetX, this.getY(), this.getZ() + offsetZ,
                    1, 0, 0.1, 0, 0.05);
        }
    }
}

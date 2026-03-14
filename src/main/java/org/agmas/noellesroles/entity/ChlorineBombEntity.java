package org.agmas.noellesroles.entity;

import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameUtils;
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

import java.util.List;

/**
 * 氯气弹实体
 * - 落地时播放FIRE_EXTINGUISH（火熄灭）声音
 * - 对半径3格内的玩家造成中毒效果
 */
public class ChlorineBombEntity extends ThrowableItemProjectile {

    // 氯气半径：3格
    private static final double CHLORINE_RADIUS = 3.0;

    public ChlorineBombEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.CHLORINE_BOMB;
    }

    @Override
    protected void onHit(net.minecraft.world.phys.HitResult hitResult) {
        super.onHit(hitResult);

        if (this.level() instanceof ServerLevel world) {
            // 播放声音 - FIRE_EXTINGUISH（火熄灭声）
            world.playSound(null, this.blockPosition(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 1.0f);

            // 对半径5格内的玩家造成中毒效果
            applyPoisonToPlayersInRadius(world);

            // 生成氯气粒子效果
            spawnChlorineParticles(world);

            this.discard();
        }
    }

    /**
     * 对半径5格内的玩家造成中毒效果
     */
    private void applyPoisonToPlayersInRadius(ServerLevel world) {
        AABB area = new AABB(
                this.getX() - CHLORINE_RADIUS, this.getY() - 1, this.getZ() - CHLORINE_RADIUS,
                this.getX() + CHLORINE_RADIUS, this.getY() + 3, this.getZ() + CHLORINE_RADIUS
        );

        List<ServerPlayer> players = world.getEntitiesOfClass(
                ServerPlayer.class, area,
                player -> GameUtils.isPlayerAliveAndSurvival(player)
        );

        for (ServerPlayer player : players) {
            // 设置玩家中毒状态（参考毒针的实现）
            SREPlayerPoisonComponent poisonComponent = SREPlayerPoisonComponent.KEY.get(player);

            // 设置中毒时间为30秒（600 ticks），投掷者为攻击者
            Player thrower = this.getOwner() instanceof Player ? (Player) this.getOwner() : null;
            if (thrower != null) {
                poisonComponent.setPoisonTicks(600, thrower.getUUID());
            } else {
                poisonComponent.setPoisonTicks(600, null);
            }
            poisonComponent.sync();
        }
    }

    /**
     * 生成氯气粒子效果
     */
    private void spawnChlorineParticles(ServerLevel world) {
        // 使用喷嚏粒子模拟氯气
        for (int i = 0; i < 80; i++) {
            double offsetX = (this.random.nextDouble() - 0.5) * CHLORINE_RADIUS * 2;
            double offsetY = this.random.nextDouble() * 3;
            double offsetZ = (this.random.nextDouble() - 0.5) * CHLORINE_RADIUS * 2;

            // 使用喷嚏粒子
            world.sendParticles(ParticleTypes.SNEEZE,
                    this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ,
                    1, 0.1, 0.1, 0.1, 0.03);
        }
    }
}

package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.content.entity.ServerGrenadeAreaManager.Type;
import org.agmas.noellesroles.init.ModItems;

/**
 * 粘液弹实体：右键投掷，命中方块/实体后在落点形成半径 4 的粘液区域，
 * 范围内玩家无法跳跃且移动缓慢（缓慢 III，见 {@link ServerGrenadeAreaManager}）。
 */
public class SlimeGrenadeEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {

    /** 粘液区域半径。 */
    public static final double AREA_RADIUS = 4.0;
    /** 粘液区域持续时间：8 秒。 */
    private static final int AREA_DURATION_TICKS = 160;

    public SlimeGrenadeEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType,
            Level world) {
        super(entityType, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.SLIME_GRENADE;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel world) {
            world.playSound(null, this.blockPosition(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.5F, 0.8F);
            ServerGrenadeAreaManager.createArea(world, this.position(), AREA_RADIUS, AREA_DURATION_TICKS,
                    Type.SLIME, this.getOwner() != null ? this.getOwner().getUUID() : null);
            for (int i = 0; i < 60; i++) {
                double ox = (this.random.nextDouble() - 0.5) * AREA_RADIUS * 2;
                double oz = (this.random.nextDouble() - 0.5) * AREA_RADIUS * 2;
                double oy = this.random.nextDouble() * 0.6;
                world.sendParticles(ParticleTypes.ITEM_SLIME, this.getX() + ox, this.getY() + oy, this.getZ() + oz,
                        2, 0.1, 0.05, 0.1, 0.02);
            }
            this.discard();
        }
    }
}

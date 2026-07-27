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
 * 燃烧弹实体：右键投掷，命中方块/实体后在落点形成半径 4 的燃烧区域，
 * 范围内玩家持续站立满 2 秒即死亡（见 {@link ServerGrenadeAreaManager}）。
 */
public class IncendiaryGrenadeEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {

    /** 燃烧区域半径。 */
    public static final double AREA_RADIUS = 4.0;
    /** 燃烧区域持续时间：7 秒。 */
    private static final int AREA_DURATION_TICKS = 140;

    public IncendiaryGrenadeEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType,
            Level world) {
        super(entityType, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.INCENDIARY_GRENADE;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel world) {
            world.playSound(null, this.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5F, 0.9F);
            ServerGrenadeAreaManager.createArea(world, this.position(), AREA_RADIUS, AREA_DURATION_TICKS,
                    Type.FIRE, this.getOwner() != null ? this.getOwner().getUUID() : null);
            for (int i = 0; i < 60; i++) {
                double ox = (this.random.nextDouble() - 0.5) * AREA_RADIUS * 2;
                double oz = (this.random.nextDouble() - 0.5) * AREA_RADIUS * 2;
                double oy = this.random.nextDouble() * 1.5;
                world.sendParticles(ParticleTypes.FLAME, this.getX() + ox, this.getY() + oy, this.getZ() + oz,
                        2, 0.1, 0.1, 0.1, 0.03);
            }
            this.discard();
        }
    }
}

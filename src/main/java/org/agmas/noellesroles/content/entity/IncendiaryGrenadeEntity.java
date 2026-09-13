/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import io.wifi.starrailexpress.util.ParticleFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.content.entity.ServerGrenadeAreaManager.Type;
import org.agmas.noellesroles.init.ModEntities;
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

    /** Allows role-specific incendiary grenades to use a different area type. */
    protected Type getAreaType() {
        return Type.FIRE;
    }

    /** Creates the projectile used by this item. Subclasses can provide a role-specific entity. */
    protected IncendiaryGrenadeEntity createGrenadeEntity(Level world) {
        return new IncendiaryGrenadeEntity(ModEntities.INCENDIARY_GRENADE, world);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel world) {
            world.playSound(null, this.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5F, 0.9F);
            ServerGrenadeAreaManager.createArea(world, this.position(), AREA_RADIUS, AREA_DURATION_TICKS,
                    getAreaType(), this.getOwner() != null ? this.getOwner().getUUID() : null);
            // 60 次循环 ×2 粒 = 120 粒，一个包发完
            ParticleFx.burst(world, ParticleTypes.FLAME,
                    this.getX(), this.getY() + 0.75, this.getZ(),
                    120, AREA_RADIUS * 0.5, 0.75, AREA_RADIUS * 0.5, 0.03);
            this.discard();
        }
    }
}

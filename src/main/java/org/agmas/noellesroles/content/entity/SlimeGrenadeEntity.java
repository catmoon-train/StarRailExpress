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
            // 60 次循环 ×2 粒 = 120 粒，一个包发完
            ParticleFx.burst(world, ParticleTypes.ITEM_SLIME,
                    this.getX(), this.getY() + 0.3, this.getZ(),
                    120, AREA_RADIUS * 0.5, 0.3, AREA_RADIUS * 0.5, 0.02);
            this.discard();
        }
    }
}

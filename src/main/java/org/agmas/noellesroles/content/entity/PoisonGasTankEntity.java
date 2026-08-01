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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;

/**
 * 毒气瓶投掷实体
 * - 落地后生成毒气云实体
 */
public class PoisonGasTankEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {

    public PoisonGasTankEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.POISON_GAS_TANK;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);

        if (this.level() instanceof ServerLevel world) {
            // 播放落地音效
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ARMADILLO_PEEK, SoundSource.PLAYERS, 1.0f, 1.0f);
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.PUFFER_FISH_BLOW_OUT, SoundSource.PLAYERS, 1.0f, 1.0f);

            // 生成毒气云实体
            PoisonGasCloudEntity gasCloud = new PoisonGasCloudEntity(ModEntities.POISON_GAS_CLOUD_ENTITY, world);
            gasCloud.setPos(this.getX(), this.getY(), this.getZ());
            if (this.getOwner() instanceof ServerPlayer owner) {
                gasCloud.setOwnerUuid(owner.getUUID());
            }
            world.addFreshEntity(gasCloud);

            this.discard();
        }
    }
}

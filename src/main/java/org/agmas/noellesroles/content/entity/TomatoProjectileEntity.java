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
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.content.item.TomatoItem;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.innocence.TomatoHeadRoleData;

public class TomatoProjectileEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {

    public TomatoProjectileEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType,
            Level world) {
        super(entityType, world);
    }

    public TomatoProjectileEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType,
            LivingEntity owner, Level world) {
        super(entityType, owner, world);
        setItem(new ItemStack(ModItems.TOMATO));
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.TOMATO;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (entity == getOwner()) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof ServerPlayer target
                && GameUtils.isPlayerAliveAndSurvival(target)) {
            TomatoHeadRoleData.giveTomato(target);
            TomatoHeadRoleData.applyTomatoSauce(target);
            TomatoItem.applyItemCooldown(target);
            if (getOwner() instanceof ServerPlayer thrower) {
                TomatoItem.applyItemCooldown(thrower);
            }
        } else {
            TomatoItem.spawnGroundDrop(serverLevel, getX(), getY(), getZ(),
                    getDeltaMovement().scale(0.15), getOwner());
        }
        discard();
    }
}

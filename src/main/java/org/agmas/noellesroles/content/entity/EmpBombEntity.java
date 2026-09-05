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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.game.roles.neutral.silver_wing.SilverWingEffects;
import org.agmas.noellesroles.init.ModItems;

import java.util.List;

/** 电磁脉冲炸弹实体：命中玩家后使其 4 秒无法使用物品并获得缓慢 III。 */
public class EmpBombEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {
    private static final double NEAR_MISS_RADIUS = 1.25D;

    public EmpBombEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.EMP_BOMB;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != getOwner() && super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!(this.level() instanceof ServerLevel world)) {
            return;
        }
        world.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.9f, 1.4f);
        world.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 24, 0.35D, 0.35D, 0.35D, 0.08D);

        if (hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof ServerPlayer hitPlayer
                && GameUtils.isPlayerAliveAndSurvival(hitPlayer)
                && hitPlayer != getOwner()) {
            SilverWingEffects.applyEmp(hitPlayer);
        } else {
            AABB area = getBoundingBox().inflate(NEAR_MISS_RADIUS);
            List<ServerPlayer> players = world.getEntitiesOfClass(ServerPlayer.class, area,
                    player -> GameUtils.isPlayerAliveAndSurvival(player) && player != getOwner());
            for (ServerPlayer player : players) {
                SilverWingEffects.applyEmp(player);
            }
        }
        this.discard();
    }
}

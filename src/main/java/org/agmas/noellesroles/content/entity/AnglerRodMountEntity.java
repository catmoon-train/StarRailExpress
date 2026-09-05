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

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerRules;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerWorldMemory;
import org.agmas.noellesroles.init.ModItems;

import java.util.UUID;

/**
 * 垂钓者 Shift+右键钓竿后乘坐的载具。看向哪边就往哪边飞；每次乘坐消耗 1 点耐久，下来后 30 秒冷却。
 */
public class AnglerRodMountEntity extends PathfinderMob {
    private int dismountGrace = AnglerRules.ROD_RIDE_DISMOUNT_GRACE_TICKS;
    private UUID riderId;
    private boolean cooldownMarked;
    private boolean didRide;

    public AnglerRodMountEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, AnglerRules.ROD_RIDE_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void bindRider(Player player) {
        this.riderId = player.getUUID();
        this.dismountGrace = AnglerRules.ROD_RIDE_DISMOUNT_GRACE_TICKS;
        this.cooldownMarked = false;
        this.didRide = false;
    }

    @Override
    public void tick() {
        this.setNoGravity(true);
        if (shouldFreeze()) {
            this.setDeltaMovement(Vec3.ZERO);
            super.tick();
            return;
        }
        super.tick();
        if (level().isClientSide) {
            return;
        }
        Entity passenger = getFirstPassenger();
        if (dismountGrace > 0) {
            dismountGrace--;
            if (passenger == null && riderId != null) {
                Player rider = level().getPlayerByUUID(riderId);
                if (rider != null && GameUtils.isPlayerAliveAndSurvival(rider)) {
                    rider.startRiding(this, true);
                }
            }
            return;
        }
        if (passenger instanceof Player player) {
            this.didRide = true;
            if (!GameUtils.isPlayerAliveAndSurvival(player) || shouldEject()) {
                finishRide(player);
            }
            return;
        }
        Player was = riderId == null ? null : level().getPlayerByUUID(riderId);
        finishRide(was);
    }

    private boolean shouldFreeze() {
        SREGameTimeComponent time = SREGameTimeComponent.KEY.get(level());
        return time != null && time.isTimeFrozen();
    }

    private boolean shouldEject() {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level());
        return game == null || !game.isRunning() || !game.isSkillAvailable;
    }

    private void finishRide(Player rider) {
        if (!cooldownMarked && didRide && rider != null) {
            cooldownMarked = true;
            AnglerWorldMemory.markDismount(rider, AnglerRules.ROD_RIDE_COOLDOWN_TICKS);
            rider.getCooldowns().addCooldown(ModItems.ANGLER_ROD, AnglerRules.ROD_RIDE_COOLDOWN_TICKS);
        }
        ejectPassengers();
        discard();
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (!(getControllingPassenger() instanceof Player player) || !isControlledByLocalInstance()) {
            super.travel(travelVector);
            return;
        }
        if (shouldFreeze()) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        this.setYRot(player.getYRot());
        this.setXRot(player.getXRot());
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();

        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0E-6) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        float speed = AnglerRules.ROD_RIDE_SPEED;
        Vec3 motion = look.scale(player.zza * speed).add(right.scale(-player.xxa * speed));
        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity passenger = getFirstPassenger();
        return passenger instanceof LivingEntity living ? living : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0, 0.35, 0.0);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }
}

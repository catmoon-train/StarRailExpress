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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerRules;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerWorldMemory;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 垂钓者 Shift+右键钓竿后乘坐的载具。只能水平平移，最多 6 秒；
 * 卡进方块后消失，并把乘客送回路上最后的安全点。
 */
public class AnglerRodMountEntity extends PathfinderMob {
    private int dismountGrace = AnglerRules.ROD_RIDE_DISMOUNT_GRACE_TICKS;
    private int rideTicks;
    private UUID riderId;
    private boolean cooldownMarked;
    private boolean didRide;
    private double hoverY;
    private boolean hoverLocked;
    private final List<Vec3> path = new ArrayList<>();

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
        this.rideTicks = 0;
        this.cooldownMarked = false;
        this.didRide = false;
        this.hoverY = player.getY();
        this.hoverLocked = true;
        this.path.clear();
        recordPath(player.position());
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
        lockHoverHeight();
        if (level().isClientSide) {
            return;
        }
        Entity passenger = getFirstPassenger();
        if (++rideTicks >= AnglerRules.ROD_RIDE_MAX_TICKS) {
            Player rider = passenger instanceof Player player ? player
                    : riderId == null ? null : level().getPlayerByUUID(riderId);
            if (rider instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.angler.ride_timeout")
                        .withStyle(ChatFormatting.AQUA), true);
            }
            finishRide(rider);
            return;
        }
        if (dismountGrace > 0) {
            dismountGrace--;
            if (passenger == null && riderId != null) {
                Player rider = level().getPlayerByUUID(riderId);
                if (rider != null && GameUtils.isPlayerAliveAndSurvival(rider)) {
                    rider.startRiding(this, true);
                }
            }
            if (passenger instanceof Player player && isSafeForPlayer(player, feetPos())) {
                recordPath(feetPos());
            }
            return;
        }
        if (passenger instanceof Player player) {
            this.didRide = true;
            if (!GameUtils.isPlayerAliveAndSurvival(player) || shouldEject()) {
                finishRide(player);
                return;
            }
            if (isStuck(player)) {
                crashIntoBlock(player);
                return;
            }
            recordPath(feetPos());
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

    private void lockHoverHeight() {
        if (!hoverLocked) {
            return;
        }
        if (Math.abs(getY() - hoverY) > 1.0E-3) {
            setPos(getX(), hoverY, getZ());
        }
        Vec3 motion = getDeltaMovement();
        if (motion.y != 0.0) {
            setDeltaMovement(motion.x, 0.0, motion.z);
        }
        setXRot(0.0f);
    }

    private Vec3 feetPos() {
        return new Vec3(getX(), hoverLocked ? hoverY : getY(), getZ());
    }

    private void recordPath(Vec3 pos) {
        if (path.isEmpty() || path.getLast().distanceToSqr(pos) >= 0.04) {
            path.add(pos);
            while (path.size() > AnglerRules.ROD_RIDE_PATH_CAP) {
                path.removeFirst();
            }
        }
    }

    private boolean isStuck(Player rider) {
        if (isInWall()) {
            return true;
        }
        if (!level().noCollision(rider, rider.getBoundingBox())) {
            return true;
        }
        return !isSafeForPlayer(rider, feetPos());
    }

    private boolean isSafeForPlayer(Player rider, Vec3 feet) {
        EntityDimensions dims = rider.getDimensions(Pose.STANDING);
        AABB box = dims.makeBoundingBox(feet.x, feet.y, feet.z);
        return level().noCollision(rider, box);
    }

    private Vec3 findSafeAlongPath(Player rider) {
        for (int i = path.size() - 1; i >= 0; i--) {
            Vec3 pos = path.get(i);
            if (isSafeForPlayer(rider, pos)) {
                return pos;
            }
        }
        Vec3 origin = path.isEmpty() ? feetPos() : path.getFirst();
        if (isSafeForPlayer(rider, origin)) {
            return origin;
        }
        for (int radius = 1; radius <= 4; radius++) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0;
                Vec3 candidate = origin.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
                if (isSafeForPlayer(rider, candidate)) {
                    return candidate;
                }
            }
        }
        return origin;
    }

    private void crashIntoBlock(Player rider) {
        Vec3 safe = findSafeAlongPath(rider);
        markCooldown(rider);
        ejectPassengers();
        if (rider instanceof ServerPlayer serverPlayer) {
            serverPlayer.teleportTo(safe.x, safe.y, safe.z);
            serverPlayer.setDeltaMovement(Vec3.ZERO);
            serverPlayer.hasImpulse = true;
            serverPlayer.hurtMarked = true;
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.angler.ride_stuck")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
        level().playSound(null, safe.x, safe.y, safe.z, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 0.7f);
        discard();
    }

    private void finishRide(Player rider) {
        markCooldown(rider);
        ejectPassengers();
        discard();
    }

    private void markCooldown(Player rider) {
        if (!cooldownMarked && didRide && rider != null) {
            cooldownMarked = true;
            AnglerWorldMemory.markDismount(rider, AnglerRules.ROD_RIDE_COOLDOWN_TICKS);
            rider.getCooldowns().addCooldown(ModItems.ANGLER_ROD, AnglerRules.ROD_RIDE_COOLDOWN_TICKS);
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (!(getControllingPassenger() instanceof Player player) || !shouldSteer()) {
            super.travel(travelVector);
            return;
        }
        if (shouldFreeze()) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        this.setYRot(player.getYRot());
        this.setXRot(0.0f);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();

        Vec3 forward = horizontalForward(player);
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        float speed = AnglerRules.ROD_RIDE_SPEED;
        Vec3 motion = forward.scale(player.zza * speed).add(right.scale(-player.xxa * speed));
        motion = new Vec3(motion.x, 0.0, motion.z);
        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);
    }

    private boolean shouldSteer() {
        return isControlledByLocalInstance() || !level().isClientSide;
    }

    private static Vec3 horizontalForward(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-6) {
            float yaw = player.getYRot() * ((float) Math.PI / 180.0f);
            return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }
        return flat.normalize();
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

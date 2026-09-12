/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.modifier.twin_children;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Invisible seat fixed on the lower twin. The upper twin rides this entity
 * instead of the lower player, so vanilla passenger packets and interpolation
 * keep working.
 */
public class TwinChildrenSeatEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> LOWER_UUID = SynchedEntityData.defineId(
            TwinChildrenSeatEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> UPPER_UUID = SynchedEntityData.defineId(
            TwinChildrenSeatEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public TwinChildrenSeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
        this.setInvulnerable(true);
        this.setSilent(true);
    }

    public void bind(Player lower, Player upper) {
        this.entityData.set(LOWER_UUID, Optional.ofNullable(lower == null ? null : lower.getUUID()));
        this.entityData.set(UPPER_UUID, Optional.ofNullable(upper == null ? null : upper.getUUID()));
        if (lower != null) {
            snapTo(lower);
        }
    }

    public void snapTo(Player lower) {
        double y = TwinChildrenHitbox.headSeatY(lower.getY(), lower.getScale());
        this.setPos(lower.getX(), y, lower.getZ());
        this.setYRot(lower.getYRot());
        this.setXRot(0.0F);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = getX();
        this.yo = getY();
        this.zo = getZ();
    }

    @Nullable
    public UUID getLowerUuid() {
        return this.entityData.get(LOWER_UUID).orElse(null);
    }

    @Nullable
    public UUID getUpperUuid() {
        return this.entityData.get(UPPER_UUID).orElse(null);
    }

    @Nullable
    public Player getLowerPlayer() {
        UUID id = getLowerUuid();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    @Nullable
    public Player getUpperPlayer() {
        UUID id = getUpperUuid();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
        Player lower = getLowerPlayer();
        if (lower != null) {
            snapTo(lower);
        }
        super.tick();
        this.setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            return;
        }
        Player upper = getUpperPlayer();
        if (lower == null || upper == null
                || !GameUtils.isPlayerAliveAndSurvival(lower)
                || !GameUtils.isPlayerAliveAndSurvival(upper)
                || lower.level() != level()
                || upper.level() != level()) {
            ejectPassengers();
            discard();
            return;
        }
        Entity passenger = getFirstPassenger();
        if (passenger != upper) {
            if (passenger != null) {
                passenger.stopRiding();
            }
            upper.startRiding(this, true);
        }
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        // Position is authored from the lower twin; ignore physics.
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        UUID upperId = getUpperUuid();
        return this.getPassengers().isEmpty()
                && upperId != null
                && passenger.getUUID().equals(upperId);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0, TwinChildrenHitbox.SEAT_PASSENGER_ATTACHMENT_Y, 0.0);
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return false;
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LOWER_UUID, Optional.empty());
        builder.define(UPPER_UUID, Optional.empty());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}

/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.init.ModItems;

/** 鸟兽兽巡飞弹实体：按发射者视野方向飞行，撞人或地面爆炸。 */
public class NiaoshoushouMissileEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {
    private static final int MAX_LIFETIME_TICKS = 20 * 20;
    private static final float EXPLOSION_RADIUS = 5.0F;
    private int steering;
    private float controlYaw;
    private float controlPitch;
    private int controlTimeout;
    private boolean exploded;

    public NiaoshoushouMissileEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType,
            Level level) {
        super(entityType, level);
        setNoGravity(true);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.NIAOSHOU_SHOU_MISSILE;
    }

    public boolean controlledBy(Player player) {
        return getOwner() == player;
    }

    /** 巡飞弹不会在发射瞬间撞到自己的发射者。 */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != getOwner() && super.canHitEntity(entity);
    }

    /**
     * 巡飞弹的朝向完全由控制输入维护，不再让基类按当前速度重写 yRot/xRot。
     * 基类的 updateRotation() 会把朝向存成"投射物坐标系"（实际朝向 + 180°），
     * 与控制包里的玩家坐标系混用会让插值每 tick 大幅过冲，导致乱转。
     */
    @Override
    protected void updateRotation() {
    }

    /**
     * 基类按速度把 yRot 存成投射物坐标系，这里改回发射者的玩家坐标系，
     * 保证发射后第一个 tick 的 getLookAngle() 就指向发射方向，不会反向飞行。
     */
    @Override
    public void shootFromRotation(Entity shooter, float pitch, float yaw, float roll, float speed,
            float divergence) {
        super.shootFromRotation(shooter, pitch, yaw, roll, speed, divergence);
        setRot(yaw, pitch);
        yRotO = yaw;
        xRotO = pitch;
    }

    public void setSteering(int steering) {
        this.steering = Integer.compare(steering, 0);
    }

    /**
     * Updates the missile's target orientation. The view direction is the primary control;
     * A/D only adds a small correction so the missile is easier to line up than the old
     * fixed five-degree-per-tick steering model.
     */
    public void setControlRotation(float yaw, float pitch, int steering) {
        this.controlYaw = yaw;
        this.controlPitch = Mth.clamp(pitch, -75.0F, 75.0F);
        this.steering = Integer.compare(steering, 0);
        this.controlTimeout = 10;
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            if (tickCount >= MAX_LIFETIME_TICKS || !(getOwner() instanceof ServerPlayer owner)
                    || !owner.isAlive()) {
                explode();
                return;
            }
            if (controlTimeout > 0) {
                controlTimeout--;
                float targetYaw = controlYaw + steering * 2.5F;
                setYRot(Mth.rotLerp(0.55F, getYRot(), targetYaw));
                setXRot(Mth.lerp(0.55F, getXRot(), controlPitch));
            }
            Vec3 direction = getLookAngle();
            if (direction.lengthSqr() > 0.001D) {
                setDeltaMovement(direction.normalize().scale(0.9D));
            }
        }
        super.tick();
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!level().isClientSide) {
            explode();
        }
    }

    private void explode() {
        if (exploded || isRemoved()) {
            return;
        }
        exploded = true;
        if (level() instanceof ServerLevel serverLevel) {
            Entity owner = getOwner();
            serverLevel.explode(owner, getX(), getY(), getZ(), EXPLOSION_RADIUS, false,
                    Level.ExplosionInteraction.NONE);
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 100,
                    EXPLOSION_RADIUS * 0.5D, 1.0D, EXPLOSION_RADIUS * 0.5D, 0.08D);
            ServerGrenadeAreaManager.scheduleFireKill(serverLevel, position(), EXPLOSION_RADIUS, 30,
                    owner instanceof ServerPlayer serverOwner ? serverOwner.getUUID() : null);
            if (owner instanceof ServerPlayer serverOwner) {
                serverOwner.connection.send(new ClientboundSetCameraPacket(serverOwner));
            }
            serverLevel.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 2.0F, 0.85F);
        }
        discard();
    }
}

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
import org.agmas.noellesroles.init.ModItems;

/** 鸟兽兽巡飞弹实体：自动前进，可由发射者左右控制，撞人或地面爆炸。 */
public class NiaoshoushouMissileEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {
    private static final int MAX_LIFETIME_TICKS = 20 * 20;
    private static final float EXPLOSION_RADIUS = 5.0F;
    private int steering;
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

    public void setSteering(int steering) {
        this.steering = Integer.compare(steering, 0);
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            if (tickCount >= MAX_LIFETIME_TICKS || !(getOwner() instanceof ServerPlayer owner)
                    || !owner.isAlive()) {
                explode();
                return;
            }
            if (steering != 0) {
                setYRot(getYRot() + steering * 5.0F);
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
            for (ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(this) <= EXPLOSION_RADIUS * EXPLOSION_RADIUS) {
                    player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 5 * 20));
                }
            }
            if (owner instanceof ServerPlayer serverOwner) {
                serverOwner.connection.send(new ClientboundSetCameraPacket(serverOwner));
            }
            serverLevel.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 2.0F, 0.85F);
        }
        discard();
    }
}

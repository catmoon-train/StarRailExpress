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

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;

import java.util.UUID;

/**
 * 竹枪延伸体：从持有者眼前沿视线伸长，最长 10 格、最多 3 秒；命中玩家则击杀并收回。
 */
public class BambooSpearEntity extends Entity {

    public static final float MAX_LENGTH = 10.0f;
    public static final int EXTEND_TICKS = 20 * 3;
    public static final int RETRACT_TICKS = 8;

    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);

    private UUID ownerUuid;
    private boolean retracting;
    private boolean killed;

    public BambooSpearEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setup(ServerPlayer owner) {
        this.ownerUuid = owner.getUUID();
        this.setPos(owner.getEyePosition());
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        this.entityData.set(LENGTH, 0.4f);
    }

    public float getLength() {
        return this.entityData.get(LENGTH);
    }

    public static boolean hasActiveFor(Player player) {
        if (player.level().isClientSide) {
            return false;
        }
        AABB box = player.getBoundingBox().inflate(16.0);
        for (BambooSpearEntity spear : player.level().getEntitiesOfClass(BambooSpearEntity.class, box)) {
            if (player.getUUID().equals(spear.ownerUuid)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LENGTH, 0.4f);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player owner = this.ownerUuid == null ? null : this.level().getPlayerByUUID(this.ownerUuid);
        if (!(owner instanceof ServerPlayer serverOwner) || !GameUtils.isPlayerAliveAndSurvival(serverOwner)
                || (!serverOwner.getMainHandItem().is(ModItems.BAMBOO_SPEAR)
                        && !serverOwner.getOffhandItem().is(ModItems.BAMBOO_SPEAR))) {
            this.discard();
            return;
        }

        Vec3 start = serverOwner.getEyePosition();
        Vec3 look = serverOwner.getLookAngle();
        this.setPos(start);
        this.setYRot(serverOwner.getYRot());
        this.setXRot(serverOwner.getXRot());
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        float length = getLength();
        if (!this.retracting) {
            length = Math.min(MAX_LENGTH, length + MAX_LENGTH / EXTEND_TICKS);
        } else {
            length = Math.max(0.0f, length - MAX_LENGTH / RETRACT_TICKS);
            if (length <= 0.05f) {
                this.discard();
                return;
            }
        }

        Vec3 end = start.add(look.scale(length));
        BlockHitResult blockHit = this.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, serverOwner));
        if (blockHit.getType() != HitResult.Type.MISS) {
            length = (float) start.distanceTo(blockHit.getLocation());
            end = start.add(look.scale(Math.max(0.05, length)));
            if (!this.retracting) {
                this.retracting = true;
            }
        }

        if (!this.retracting && !this.killed) {
            AABB sweep = new AABB(start, end).inflate(0.4);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(serverOwner, start, end, sweep,
                    entity -> entity instanceof ServerPlayer target
                            && target != serverOwner
                            && GameUtils.isPlayerAliveAndSurvival(target),
                    length * length);
            if (entityHit != null && entityHit.getEntity() instanceof ServerPlayer target) {
                this.killed = true;
                this.retracting = true;
                Vec3 hit = entityHit.getLocation();
                serverLevel.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 12, 0.2, 0.2, 0.2, 0.15);
                serverLevel.playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
                        1.0f, 1.1f);
                GameUtils.killPlayer(target, true, serverOwner, Noellesroles.id("bamboo_spear"));
                length = (float) start.distanceTo(hit);
            }
        }

        if (!this.retracting && (this.tickCount >= EXTEND_TICKS || length >= MAX_LENGTH)) {
            this.retracting = true;
        }

        this.entityData.set(LENGTH, length);
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
    protected void readAdditionalSaveData(CompoundTag compoundTag) {
        if (compoundTag.hasUUID("OwnerUuid")) {
            this.ownerUuid = compoundTag.getUUID("OwnerUuid");
        }
        this.retracting = compoundTag.getBoolean("Retracting");
        this.killed = compoundTag.getBoolean("Killed");
        this.entityData.set(LENGTH, compoundTag.getFloat("Length"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {
        if (this.ownerUuid != null) {
            compoundTag.putUUID("OwnerUuid", this.ownerUuid);
        }
        compoundTag.putBoolean("Retracting", this.retracting);
        compoundTag.putBoolean("Killed", this.killed);
        compoundTag.putFloat("Length", getLength());
    }
}

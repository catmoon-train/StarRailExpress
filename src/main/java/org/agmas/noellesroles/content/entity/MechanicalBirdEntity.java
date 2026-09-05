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

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.neutral.silver_wing.SilverWingEffects;
import org.agmas.noellesroles.game.roles.neutral.silver_wing.SilverWingRules;
import org.agmas.noellesroles.packet.MechanicalBirdControlC2SPacket;
import org.agmas.noellesroles.role_data.neutral.SilverWingRoleData;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** 银翼机械小鸟：玩家控制飞行，附近持续失明，碰到玩家/敌人或冲刺后爆炸。 */
public class MechanicalBirdEntity extends LivingEntity {
    private static final double MAX_CONTROL_DISTANCE_SQR = 128.0D * 128.0D;
    private static final EntityDataAccessor<Integer> DASH_REMAINING =
            SynchedEntityData.defineId(MechanicalBirdEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID ownerUuid;
    private int life;
    private int controlTimeout;
    private int movementBits;
    private boolean cameraBound;
    private boolean exploded;
    private boolean sneakWasDown;
    private boolean dashing;

    public MechanicalBirdEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.life = SilverWingRules.ticks(SilverWingRules.BIRD_LIFETIME_SECONDS);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 6.0);
    }

    public void setOwner(Player owner) {
        this.ownerUuid = owner.getUUID();
    }

    public boolean controlledBy(Player player) {
        return ownerUuid != null && ownerUuid.equals(player.getUUID());
    }

    @Nullable
    public Player getOwnerPlayer() {
        if (ownerUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getPlayerByUUID(ownerUuid);
    }

    public int getDashRemainingTicks() {
        return entityData.get(DASH_REMAINING);
    }

    public boolean isDashing() {
        return getDashRemainingTicks() > 0;
    }

    public void setControlInput(float yaw, float pitch, int movementBits) {
        this.setYRot(yaw);
        this.setXRot(Mth.clamp(pitch, -85.0F, 85.0F));
        this.yHeadRot = yaw;
        this.movementBits = movementBits;
        this.controlTimeout = 10;
        boolean sneakDown = (movementBits & MechanicalBirdControlC2SPacket.BIT_SNEAK) != 0;
        if (sneakDown && !sneakWasDown && !dashing && !exploded) {
            startDash();
        }
        sneakWasDown = sneakDown;
    }

    private void startDash() {
        dashing = true;
        int dashTicks = SilverWingRules.ticks(SilverWingRules.BIRD_DASH_EXPLODE_SECONDS);
        entityData.set(DASH_REMAINING, dashTicks);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    SoundSource.PLAYERS, 1.1F, 1.35F);
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Player owner = getOwnerPlayer();
        if (exploded) {
            return;
        }
        if (--life <= 0 || !(owner instanceof ServerPlayer serverOwner) || !GameUtils.isPlayerAliveAndSurvival(serverOwner)) {
            despawnQuietly();
            return;
        }
        if (serverOwner.distanceToSqr(this) > MAX_CONTROL_DISTANCE_SQR
                || !AreasWorldComponent.getInstance(level()).getPlayArea().contains(position())) {
            despawnQuietly();
            return;
        }
        if (!cameraBound) {
            serverOwner.connection.send(new ClientboundSetCameraPacket(this));
            cameraBound = true;
        }

        if (dashing) {
            int remaining = entityData.get(DASH_REMAINING) - 1;
            entityData.set(DASH_REMAINING, remaining);
            if (remaining <= 0) {
                explode();
                return;
            }
        }

        applyFlightMovement();
        applyAura(serverLevel, serverOwner);
        playFlightCue(serverLevel);
        if (collidesWithTarget(serverLevel, serverOwner)) {
            explode();
        }
    }

    private void playFlightCue(ServerLevel serverLevel) {
        int interval = dashing ? 4 : 7;
        if (tickCount % interval != 0) {
            return;
        }
        float volume = dashing ? 0.95F : 0.75F;
        float pitch = dashing ? 1.55F : 1.2F;
        serverLevel.playSound(null, blockPosition(), SoundEvents.PARROT_FLY, SoundSource.NEUTRAL, volume, pitch);
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.2D, getZ(),
                dashing ? 6 : 2, 0.12D, 0.12D, 0.12D, 0.01D);
        if (dashing) {
            serverLevel.sendParticles(ParticleTypes.FIREWORK, getX(), getY(), getZ(),
                    4, 0.08D, 0.08D, 0.08D, 0.02D);
        }
    }

    private void applyFlightMovement() {
        Vec3 look = getLookAngle();
        Vec3 motion;
        if (dashing) {
            motion = look.normalize().scale(SilverWingRules.BIRD_DASH_SPEED);
        } else {
            Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
            if (horizontal.lengthSqr() < 1.0E-6) {
                horizontal = new Vec3(0.0D, 0.0D, 1.0D);
            } else {
                horizontal = horizontal.normalize();
            }
            Vec3 right = new Vec3(-horizontal.z, 0.0D, horizontal.x);
            motion = Vec3.ZERO;
            if (controlTimeout > 0) {
                controlTimeout--;
                double speed = SilverWingRules.BIRD_FLY_SPEED;
                if ((movementBits & MechanicalBirdControlC2SPacket.BIT_FORWARD) != 0) {
                    motion = motion.add(look.scale(speed));
                }
                if ((movementBits & MechanicalBirdControlC2SPacket.BIT_BACK) != 0) {
                    motion = motion.add(look.scale(-speed));
                }
                if ((movementBits & MechanicalBirdControlC2SPacket.BIT_LEFT) != 0) {
                    motion = motion.add(right.scale(-speed));
                }
                if ((movementBits & MechanicalBirdControlC2SPacket.BIT_RIGHT) != 0) {
                    motion = motion.add(right.scale(speed));
                }
                if ((movementBits & MechanicalBirdControlC2SPacket.BIT_JUMP) != 0) {
                    motion = motion.add(0.0D, speed, 0.0D);
                }
            }
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, getDeltaMovement());
        fallDistance = 0.0F;
    }

    private void applyAura(ServerLevel serverLevel, ServerPlayer owner) {
        AABB area = getBoundingBox().inflate(SilverWingRules.BIRD_AURA_RADIUS);
        for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, area,
                target -> GameUtils.isPlayerAliveAndSurvival(target) && target != owner
                        && SilverWingRules.isWithinAura(distanceToSqr(target)))) {
            SilverWingEffects.applyBirdAura(player);
        }
    }

    private boolean collidesWithTarget(ServerLevel serverLevel, ServerPlayer owner) {
        AABB box = getBoundingBox().inflate(0.15D);
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, box, this::isExplosionTarget)) {
            if (entity != owner) {
                return true;
            }
        }
        return false;
    }

    private boolean isExplosionTarget(Entity entity) {
        if (entity == this) {
            return false;
        }
        if (entity instanceof ServerPlayer player) {
            return GameUtils.isPlayerAliveAndSurvival(player);
        }
        return entity instanceof LivingEntity living
                && living.isAlive()
                && !(entity instanceof MechanicalBirdEntity);
    }

    public void explode() {
        if (exploded || isRemoved()) {
            return;
        }
        exploded = true;
        if (level() instanceof ServerLevel serverLevel) {
            Player owner = getOwnerPlayer();
            Vec3 explosionPos = position();
            AABB area = getBoundingBox().inflate(SilverWingRules.BIRD_EXPLOSION_RADIUS);
            for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, area,
                    target -> GameUtils.isPlayerAliveAndSurvival(target)
                            && SilverWingRules.isWithinExplosion(distanceToSqr(target)))) {
                SilverWingEffects.applyBirdExplosion(player, owner, serverLevel.random, explosionPos);
            }
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 16,
                    0.55D, 0.45D, 0.55D, 0.08D);
            serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 28,
                    0.7D, 0.45D, 0.7D, 0.06D);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 48,
                    SilverWingRules.BIRD_EXPLOSION_RADIUS * 0.45D, 0.45D,
                    SilverWingRules.BIRD_EXPLOSION_RADIUS * 0.4D, 0.14D);
            serverLevel.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 1.8F, 1.15F);
            returnCamera(owner);
        }
        clearOwnerBird();
        discard();
    }

    public void despawnQuietly() {
        if (isRemoved()) {
            return;
        }
        returnCamera(getOwnerPlayer());
        clearOwnerBird();
        discard();
    }

    private void returnCamera(Player owner) {
        if (owner instanceof ServerPlayer serverOwner) {
            serverOwner.connection.send(new ClientboundSetCameraPacket(serverOwner));
        }
    }

    private void clearOwnerBird() {
        Player owner = getOwnerPlayer();
        if (owner != null) {
            RoleData.ifPresent(SilverWingRoleData.class, owner, SilverWingRoleData::clearActiveBird);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || exploded) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player && controlledBy(player)) {
            return false;
        }
        explode();
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DASH_REMAINING, 0);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        life = tag.getInt("Life");
        dashing = tag.getBoolean("Dashing");
        entityData.set(DASH_REMAINING, tag.getInt("DashRemaining"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putInt("Life", life);
        tag.putBoolean("Dashing", dashing);
        tag.putInt("DashRemaining", entityData.get(DASH_REMAINING));
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return java.util.List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}

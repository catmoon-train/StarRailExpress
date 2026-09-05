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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerCatchHandler;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerRules;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerWorldMemory;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 错误的垂钓者：坐姿无脸实体，在旧钓点巡回。不是可扮演职业。
 */
public class ErrorAnglerEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> SKIN_UUID = SynchedEntityData.defineId(
            ErrorAnglerEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private long claimUntil = 0;
    private long nextPatrolAt = 0;
    private UUID lastRider = null;
    private AnglerWorldMemory.CatchSpot currentSpot;

    public ErrorAnglerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_UUID, Optional.empty());
    }

    public void setup(UUID skin) {
        this.entityData.set(SKIN_UUID, Optional.ofNullable(skin));
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.ANGLER_ROD));
        resetWindows();
        this.currentSpot = new AnglerWorldMemory.CatchSpot(level().dimension(), blockPosition());
    }

    public UUID getSkinUuid() {
        return this.entityData.get(SKIN_UUID).orElse(null);
    }

    private void resetWindows() {
        long now = GameUtils.getTicksFromGameStart(level());
        this.claimUntil = now + AnglerRules.CLAIM_TICKS;
        this.nextPatrolAt = now + AnglerRules.randomPatrolInterval(getRandom());
    }

    @Override
    public void tick() {
        if (SREGameTimeComponent.KEY.get(level()).isTimeFrozen()) {
            return;
        }
        super.tick();
        this.setPose(Pose.SITTING);
        this.setDeltaMovement(Vec3.ZERO);
        this.noPhysics = true;
        this.setNoGravity(true);
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level());
        if (game == null || !game.isRunning() || AnglerWorldMemory.isErrorRodClaimed()) {
            discard();
            return;
        }
        if (!game.isSkillAvailable) {
            return;
        }
        long now = GameUtils.getTicksFromGameStart(level());
        tickRiderTracking();
        if (now < claimUntil) {
            tryClaim(serverLevel);
        }
        if (now >= nextPatrolAt) {
            patrol(serverLevel);
        }
    }

    private void tickRiderTracking() {
        Entity passenger = getFirstPassenger();
        UUID current = passenger instanceof Player player ? player.getUUID() : null;
        if (lastRider != null && current == null) {
            Player was = level().getPlayerByUUID(lastRider);
            if (was != null) {
                AnglerWorldMemory.markDismount(was);
            }
        }
        lastRider = current;
    }

    private void tryClaim(ServerLevel level) {
        AABB box = getBoundingBox().inflate(AnglerRules.CLAIM_RADIUS);
        List<ServerPlayer> nearby = level.getEntitiesOfClass(ServerPlayer.class, box,
                GameUtils::isPlayerAliveAndSurvival);
        if (nearby.isEmpty()) {
            return;
        }
        ItemStack rod = AnglerCatchHandler.createErrorRod();
        ItemEntity drop = new ItemEntity(level, getX(), getY() + 0.2, getZ(), rod);
        drop.setPickUpDelay(5);
        level.addFreshEntity(drop);
        AnglerWorldMemory.markErrorRodClaimed();
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1f, 0.5f);
        smoke(level);
        discard();
    }

    private void patrol(ServerLevel level) {
        AnglerWorldMemory.CatchSpot next = AnglerWorldMemory.nextPatrolSpot(currentSpot, level);
        if (next == null) {
            resetWindows();
            return;
        }
        List<Entity> riders = new ArrayList<>(getPassengers());
        ServerLevel dest = level.getServer().getLevel(next.dimension());
        if (dest == null) {
            dest = level;
        }
        double x = next.pos().getX() + 0.5;
        double y = next.pos().getY();
        double z = next.pos().getZ() + 0.5;
        smoke(level);
        if (dest != level) {
            // 跨维度极少见：丢掉骑手后在目标维重建。
            ejectPassengers();
            discard();
            AnglerWorldMemory.spawnEcho(dest, next, getSkinUuid());
            return;
        }
        this.teleportTo(x, y, z);
        this.currentSpot = next;
        for (Entity rider : riders) {
            rider.teleportTo(x, y + 1.0, z);
            rider.startRiding(this, true);
        }
        smoke(level);
        resetWindows();
    }

    private void smoke(ServerLevel level) {
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0, getZ(), 18, 0.3, 0.6, 0.3, 0.02);
        level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.0, getZ(), 8, 0.2, 0.4, 0.2, 0.01);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return false;
        }
        if (level() instanceof ServerLevel serverLevel) {
            smoke(serverLevel);
            AnglerWorldMemory.scheduleEchoRespawn(serverLevel);
        }
        discard();
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return InteractionResult.PASS;
        }
        if (!AnglerWorldMemory.canRide(player)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.angler.ride_cd")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (player.startRiding(this)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0, dimensions.height() * 0.55, 0.35);
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
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID skin = getSkinUuid();
        if (skin != null) {
            tag.putUUID("Skin", skin);
        }
        tag.putLong("ClaimUntil", claimUntil);
        tag.putLong("NextPatrol", nextPatrolAt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Skin")) {
            this.entityData.set(SKIN_UUID, Optional.of(tag.getUUID("Skin")));
        }
        this.claimUntil = tag.getLong("ClaimUntil");
        this.nextPatrolAt = tag.getLong("NextPatrol");
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.ANGLER_ROD));
    }
}

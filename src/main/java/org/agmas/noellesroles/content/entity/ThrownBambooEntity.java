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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 投掷竹子：直线飞行，途中最多将 2 名玩家挂在竹子上，撞墙后钉住；从发射起 10 秒后消失。
 */
public class ThrownBambooEntity extends AbstractArrow {

    public static final int MAX_HANG = 2;
    public static final int LIFETIME_TICKS = 20 * 10;

    private final List<UUID> hungPlayers = new ArrayList<>(2);

    public ThrownBambooEntity(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.setBaseDamage(0);
        this.pickup = AbstractArrow.Pickup.DISALLOWED;
    }

    public ThrownBambooEntity(EntityType<? extends AbstractArrow> entityType, LivingEntity livingEntity, Level level,
            ItemStack itemStack) {
        super(entityType, livingEntity, level, itemStack, null);
        this.setNoGravity(true);
        this.setBaseDamage(0);
        this.pickup = AbstractArrow.Pickup.DISALLOWED;
    }

    @Override
    protected boolean tryPickup(Player player) {
        return false;
    }

    @Override
    protected float getWaterInertia() {
        return 1F;
    }

    @Override
    public void playerTouch(Player player) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < MAX_HANG && passenger instanceof Player;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int index = this.getPassengers().indexOf(passenger);
        double side = index <= 0 ? 0.35 : -0.35;
        return new Vec3(0.0, 0.15, side);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (this.hungPlayers.size() >= MAX_HANG) {
            return false;
        }
        if (this.hungPlayers.contains(entity.getUUID())) {
            return false;
        }
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        if (this.level().isClientSide) {
            return;
        }
        if (!(entityHitResult.getEntity() instanceof ServerPlayer target)) {
            return;
        }
        if (this.getOwner() != null && target.getUUID().equals(this.getOwner().getUUID())) {
            return;
        }
        if (this.hungPlayers.contains(target.getUUID()) || this.hungPlayers.size() >= MAX_HANG) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return;
        }
        this.hungPlayers.add(target.getUUID());
        target.startRiding(this, true);

        Vec3 location = entityHitResult.getLocation();
        ServerLevel serverLevel = target.serverLevel();
        serverLevel.sendParticles(ParticleTypes.CRIT, location.x, location.y + 1.0f, location.z, 10, 0.25, 0.25,
                0.25, 0.12);
        serverLevel.playSound(null, location.x, location.y, location.z, SoundEvents.BAMBOO_WOOD_HIT, SoundSource.PLAYERS,
                1.0f, 0.9f);
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BAMBOO_WOOD_HIT,
                    SoundSource.PLAYERS, 1.0f, 0.8f);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount > LIFETIME_TICKS) {
            this.ejectPassengers();
            this.remove(RemovalReason.DISCARDED);
            return;
        }
        if (this.level().isClientSide) {
            return;
        }
        remountHungPlayers();
    }

    private void remountHungPlayers() {
        hungPlayers.removeIf(uuid -> {
            Player player = this.level().getPlayerByUUID(uuid);
            if (!(player instanceof ServerPlayer serverPlayer) || !GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                if (player != null && player.getVehicle() == this) {
                    player.stopRiding();
                }
                return true;
            }
            if (serverPlayer.getVehicle() != this) {
                serverPlayer.startRiding(this, true);
            }
            return false;
        });
    }

    @Override
    public void remove(RemovalReason reason) {
        this.ejectPassengers();
        super.remove(reason);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return Items.BAMBOO.getDefaultInstance();
    }

    @Override
    public ItemStack getPickupItemStackOrigin() {
        return ModItems.BAMBOO.getDefaultInstance();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        ListTag list = new ListTag();
        for (UUID uuid : hungPlayers) {
            list.add(NbtUtils.createUUID(uuid));
        }
        compoundTag.put("HungPlayers", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        hungPlayers.clear();
        ListTag list = compoundTag.getList("HungPlayers", Tag.TAG_INT_ARRAY);
        for (Tag tag : list) {
            hungPlayers.add(NbtUtils.loadUUID(tag));
        }
    }
}

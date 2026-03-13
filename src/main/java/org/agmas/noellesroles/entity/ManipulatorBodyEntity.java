package org.agmas.noellesroles.entity;

import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import org.agmas.noellesroles.roles.manipulator.ManipulatorPlayerComponent;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 操纵师本体实体
 */
public class ManipulatorBodyEntity extends LivingEntity {

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            ManipulatorBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private GameProfile skinProfile = null;

    private String ownerName = "";

    public static final int MAX_LIFETIME = 1000;

    private int lifetime = 0;

    private Player ownerCache = null;

    public ManipulatorBodyEntity(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
        this.setNoGravity(false);
        this.setHealth(20.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
    }

    public void setOwner(Player owner) {
        if (owner != null) {
            this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
            this.ownerCache = owner;
            this.ownerName = owner.getName().getString();

            if (owner instanceof ServerPlayer serverPlayer) {
                this.skinProfile = serverPlayer.getGameProfile();
            }

            this.setCustomName(Component.translatable("entity.manipulator_body.name", owner.getName()));
            this.setCustomNameVisible(false);
        }
    }

    public Optional<UUID> getOwnerUuid() {
        return this.entityData.get(OWNER_UUID);
    }

    public Player getOwner() {
        if (ownerCache != null && ownerCache.isAlive()) {
            return ownerCache;
        }

        Optional<UUID> ownerUuid = getOwnerUuid();
        if (ownerUuid.isPresent()) {
            ownerCache = level().getPlayerByUUID(ownerUuid.get());
            return ownerCache;
        }
        return null;
    }

    public GameProfile getSkinProfile() {
        return skinProfile;
    }

    public String getOwnerName() {
        return ownerName;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide())
            return;

        final var gameWorldComponent = StarGameWorldComponent.KEY.get(level());
        if (gameWorldComponent != null) {
            if (!gameWorldComponent.isRunning()) {
                this.discard();
                return;
            }
        }

        lifetime++;
        if (lifetime > MAX_LIFETIME) {
            this.discard();
            return;
        }

        Player owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide())
            return false;

        boolean result = super.hurt(source, amount);

        if (this.isDeadOrDying()) {
            Player owner = getOwner();
            if (owner != null) {
                ManipulatorPlayerComponent manipulatorComp = ManipulatorPlayerComponent.KEY.get(owner);
                // manipulatorComp.onBodyDeath();
            }
        }

        return result;
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);

        Player owner = getOwner();
        if (owner != null) {
            ManipulatorPlayerComponent manipulatorComp = ManipulatorPlayerComponent.KEY.get(owner);
            // manipulatorComp.onBodyDeath();
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);

        if (nbt.contains("OwnerUUID")) {
            this.entityData.set(OWNER_UUID, Optional.of(nbt.getUUID("OwnerUUID")));
        }
        if (nbt.contains("OwnerName")) {
            this.ownerName = nbt.getString("OwnerName");
        }
        this.lifetime = nbt.contains("Lifetime") ? nbt.getInt("Lifetime") : 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);

        Optional<UUID> ownerUuid = getOwnerUuid();
        ownerUuid.ifPresent(uuid -> nbt.putUUID("OwnerUUID", uuid));
        nbt.putString("OwnerName", this.ownerName);
        nbt.putInt("Lifetime", this.lifetime);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            return false;
        }
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return java.util.Collections.emptyList();
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
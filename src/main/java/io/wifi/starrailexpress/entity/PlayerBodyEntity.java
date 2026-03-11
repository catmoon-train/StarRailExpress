package io.wifi.starrailexpress.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PlayerBodyEntity extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> PLAYER = SynchedEntityData.defineId(PlayerBodyEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);

    private static final EntityDataAccessor<String> DEATH_REASON = SynchedEntityData.defineId(PlayerBodyEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> KILLER = SynchedEntityData.defineId(PlayerBodyEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);

    public PlayerBodyEntity(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PLAYER, Optional.empty());
        builder.define(KILLER, Optional.empty());
        builder.define(DEATH_REASON, "");
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return null;
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

    public void setDeathReason(String deathReason) {
        this.entityData.set(DEATH_REASON, deathReason);
    }

    public String getDeathReason() {
        String optional = this.entityData.get(DEATH_REASON);
        return optional;
    }

    public void setKillerUuid(UUID playerUuid) {
        this.entityData.set(KILLER, Optional.of(playerUuid));
    }

    public UUID getKillerUuid() {
        Optional<UUID> optional = this.entityData.get(KILLER);
        return optional.orElse(null);
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.entityData.set(PLAYER, Optional.of(playerUuid));
    }

    public UUID getPlayerUuid() {
        Optional<UUID> optional = this.entityData.get(PLAYER);
        return optional.orElse(null);
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return !damageSource.is(DamageTypes.GENERIC_KILL) && !damageSource.is(DamageTypes.FELL_OUT_OF_WORLD);
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public void push(Entity entity) {
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 999999.0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (this.getPlayerUuid() != null) {
            nbt.putUUID("Player", this.getPlayerUuid());
        }
        if (this.getKillerUuid() != null) {
            nbt.putUUID("Killer", this.getKillerUuid());
        }
        if (this.getDeathReason() != null) {
            nbt.putString("DeathReason", this.getDeathReason());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        UUID uUID = null;
        UUID killerUUID = null;
        if (nbt.hasUUID("Player")) {
            uUID = nbt.getUUID("Player");
        }
        if (nbt.hasUUID("Killer")) {
            killerUUID = nbt.getUUID("Killer");
        }
        if (uUID != null) {
            this.setPlayerUuid(uUID);
        }
        if (killerUUID != null) {
            this.setKillerUuid(killerUUID);
        }
        if(nbt.contains("DeathReason")){
            this.setDeathReason(nbt.getString("DeathReason"));
        }
    }

}

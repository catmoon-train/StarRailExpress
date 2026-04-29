package io.wifi.starrailexpress.content.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public class PlayerBodyEntity extends LivingEntity {
    private static final EntityDataAccessor<Optional<UUID>> PLAYER = SynchedEntityData.defineId(PlayerBodyEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DEATH_REASON = SynchedEntityData.defineId(PlayerBodyEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> KILLER = SynchedEntityData.defineId(PlayerBodyEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private final SimpleContainer corpseInventory = new SimpleContainer(36);

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
    public void kill() {
        this.discard();
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

    public void setCorpseInventoryFromPlayerInventory(Inventory inventory) {
        for (int i = 0; i < this.corpseInventory.getContainerSize(); i++) {
            this.corpseInventory.setItem(i, inventory.getItem(i).copy());
            inventory.setItem(i, ItemStack.EMPTY);
        }
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
        ListTag inventoryTag = new ListTag();
        for (int i = 0; i < this.corpseInventory.getContainerSize(); i++) {
            ItemStack stack = this.corpseInventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag itemTag = new CompoundTag();
            itemTag.putByte("Slot", (byte) i);
            itemTag.put("Item", stack.save(this.registryAccess()));
            inventoryTag.add(itemTag);
        }
        nbt.put("CorpseInventory", inventoryTag);
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
        if (nbt.contains("DeathReason")) {
            this.setDeathReason(nbt.getString("DeathReason"));
        }
        if (nbt.contains("CorpseInventory", Tag.TAG_LIST)) {
            ListTag inventoryTag = nbt.getList("CorpseInventory", Tag.TAG_COMPOUND);
            for (int i = 0; i < inventoryTag.size(); i++) {
                CompoundTag itemTag = inventoryTag.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot < 0 || slot >= this.corpseInventory.getContainerSize()) {
                    continue;
                }
                ItemStack stack = ItemStack.parse(this.registryAccess(), itemTag.getCompound("Item"))
                        .orElse(ItemStack.EMPTY);
                this.corpseInventory.setItem(slot, stack);
            }
        }
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 vec3, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer && hasCorpseItems()) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("container.starrailexpress.player_body");
                }

                @Override
                public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
                    return ChestMenu.threeRows(i, inventory, corpseInventory);
                }
            });
            return InteractionResult.SUCCESS;
        }
        return super.interactAt(player, vec3, hand);
    }

    private boolean hasCorpseItems() {
        for (int i = 0; i < corpseInventory.getContainerSize(); i++) {
            if (!corpseInventory.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getZ() >= 19000) {
            this.discard();
        }
    }
}

package io.wifi.starrailexpress.cca;

import java.util.HashMap;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class ExtraSlotComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<ExtraSlotComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("extra_slots"),
            ExtraSlotComponent.class);
    private final Player player;
    public final HashMap<ResourceLocation, ItemStack> SLOTS = new HashMap<>();
    private boolean syncMode = false;
    public final boolean SAVE_FLAG = false;
    public boolean fullSync = false;
    public final HashMap<ResourceLocation, Boolean> needSync = new HashMap<>();

    public static void hurtAndBreak(Player player, ItemStack stack, int damage, ResourceLocation slot) {
        KEY.get(player).hurtAndBreak(stack, damage, slot);
    }

    public static ItemStack removeSlot(Player player, ResourceLocation slot) {
        return KEY.get(player).removeSlot(slot);
    }

    public static void clear(Player player) {
        KEY.get(player).clear();
    }

    public static void setSlot(Player player, ResourceLocation slot, ItemStack item) {
        KEY.get(player).setItem(slot, item);
    }

    public static ExtraSlotComponent getComponent(Player player) {
        return KEY.get(player);
    }

    public static ItemStack getSlot(Player player, ResourceLocation slot) {
        return KEY.get(player).getItem(slot);
    }

    public ExtraSlotComponent(Player player) {
        this.player = player;
    }

    @Override
    public void readFromNbt(CompoundTag tag, Provider registryLookup) {
        if (tag.contains("remove_all")) {
            this.SLOTS.clear();
        }
        if (tag.contains("items", CompoundTag.TAG_LIST)) {
            ListTag itemLists = tag.getList("items", CompoundTag.TAG_COMPOUND);
            for (Tag itt : itemLists) {
                var ctag = (CompoundTag) itt;
                String slot = null;
                ResourceLocation slotRes = null;
                ItemStack item = null;
                if (ctag.contains("slot")) {
                    slot = ctag.getString("slot");
                }

                if (slot != null)
                    slotRes = ResourceLocation.tryParse(slot);
                if (ctag.contains("removed")) {
                    this.SLOTS.remove(slotRes);
                    continue;
                }
                if (ctag.contains("item")) {
                    var oit = ItemStack.parse(registryLookup, ctag.get("item"));
                    if (oit.isPresent()) {
                        item = oit.get();
                    }
                }
                if (item != null && slotRes != null) {
                    this.SLOTS.put(slotRes, item);
                }
            }
        }
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        syncMode = true;
        CompoundTag tag = new CompoundTag();
        this.writeToNbt(tag, buf.registryAccess());
        buf.writeNbt(tag);
        syncMode = false;
    }

    @Override
    public void writeToNbt(CompoundTag tag, Provider registryLookup) {
        if (!SAVE_FLAG && !syncMode) {
            return;
        }
        if (this.SLOTS.isEmpty()) {
            return;
        }
        ListTag listTag = new ListTag();
        for (var entry : this.SLOTS.entrySet()) {
            if (!fullSync && !needSync.getOrDefault(entry.getKey(), false))
                continue;
            CompoundTag slotTag = new CompoundTag();
            String name = entry.getKey().toString();
            ItemStack item = entry.getValue();
            slotTag.putString("slot", name);

            if (item != null && !item.isEmpty()) {
                var itTag = item.save(registryLookup);
                slotTag.put("item", itTag);
            } else {
                slotTag.putBoolean("removed", true);
            }
            listTag.add(slotTag);
        }
        tag.put("items", listTag);
        if (fullSync)
            tag.putBoolean("remove_all", true);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return true;
    }

    public Player getPlayer() {
        return this.player;
    }

    public void hurtAndBreak(ItemStack stack, int damage, ResourceLocation slot) {
        if (this.player.level().isClientSide)
            return;
        if (!(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ServerLevel serverLevel = serverPlayer.serverLevel();
        if (stack.isDamageableItem()) {
            if (!player.hasInfiniteMaterials()) {
                if (damage > 0) {
                    damage = EnchantmentHelper.processDurabilityChange(serverLevel, stack, damage);
                    if (damage <= 0) {
                        return;
                    }
                }

                if (player != null && damage != 0) {
                    CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(serverPlayer, stack,
                            stack.getDamageValue() + damage);
                }

                int j = stack.getDamageValue() + damage;
                stack.setDamageValue(j);
                if (j >= stack.getMaxDamage()) {
                    stack.shrink(1);
                    player.playNotifySound(SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1f, 1f);
                }
            }
        }
        needSync.put(slot, true);
        sync();
    }

    public void clear() {
        this.SLOTS.clear();
        fullSync = true;
        sync();
    }

    public void syncWith(ServerPlayer sp) {
        fullSync = true;
        KEY.syncWith(sp, this.player.asComponentProvider());
        fullSync = false;
    }

    public void fullSync() {
        fullSync = true;
        sync();
    }

    public void sync() {
        KEY.sync(this.player);
        fullSync = false;
        needSync.clear();
    }

    public boolean hasItemStack(ItemStack itemStack) {
        return this.SLOTS.containsValue(itemStack);
    }

    public ItemStack removeSlot(@NotNull ResourceLocation slot) {
        needSync.put(slot, true);
        var it = this.SLOTS.remove(slot);
        fullSync();
        return it;
    }

    public boolean hasItem(@NotNull ResourceLocation slot, Predicate<Holder<Item>> predicate) {
        return this.getItem(slot).is(predicate);
    }

    public boolean hasItem(@NotNull ResourceLocation slot, Item item) {
        return this.getItem(slot).is(item);
    }

    public boolean hasItem(@NotNull ResourceLocation slot) {
        return this.SLOTS.containsKey(slot);
    }

    public @NotNull void setItem(@NotNull ResourceLocation slot, @NotNull ItemStack itemStack) {
        this.SLOTS.put(slot, itemStack);
        needSync.put(slot, true);
        sync();
    }

    public @NotNull ItemStack getItem(@NotNull ResourceLocation slot) {
        return this.SLOTS.getOrDefault(slot, ItemStack.EMPTY);
    }

    @Override
    public void init() {
        this.clear();
    }

    @Override
    public void clientTick() {
        if (!needSync.isEmpty())
            needSync.clear();

        int slot = 0;
        for (var entry : this.SLOTS.entrySet()) {
            slot++;
            var it = entry.getValue();
            it.getItem().inventoryTick(it, this.player.level(), player, -slot, false);
        }
    }

    @Override
    public void serverTick() {
        int slot = 0;
        for (var entry : this.SLOTS.entrySet()) {
            slot++;
            var it = entry.getValue();
            it.getItem().inventoryTick(it, this.player.level(), player, -slot, false);
        }
    }
}

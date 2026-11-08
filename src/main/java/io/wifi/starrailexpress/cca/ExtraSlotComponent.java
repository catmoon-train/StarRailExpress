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

package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
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
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.function.Predicate;

public class ExtraSlotComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<ExtraSlotComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("extra_slots"),
            ExtraSlotComponent.class);
    private final Player player;
    public final HashMap<ResourceLocation, ItemStack> SLOTS = new HashMap<>();
    private boolean syncMode = false;
    public final boolean SAVE_FLAG = false;
    public boolean fullSync = false;
    public final HashMap<ResourceLocation, Boolean> needSync = new HashMap<>();
    /** 默认态（没有任何槽位数据）时不向观察者发包，见 {@link DefaultStateSyncGate}。 */
    private final DefaultStateSyncGate syncGate;

    public static ItemStack hurtAndBreak(Player player, ItemStack stack, int damage, ResourceLocation slot) {
        return KEY.get(player).hurtAndBreak(stack, damage, slot);
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
        this.syncGate = new DefaultStateSyncGate(player);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
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
        this.writeToSyncNbt(tag, buf.registryAccess());
        buf.writeNbt(tag);
        syncMode = false;
        // 只有这里能拿到 recipient：记录本次发出去的是默认态还是真实数据，
        // 状态回落默认时才能给「拿到过数据」的观察者补发一次复位包。
        //
        // 判定必须用「客户端是否真的被复位」而不是「SLOTS 是否为空」：
        //   fullSync=true  → tag 带 remove_all，客户端会 clear() 再重填 → 真复位；
        //   fullSync=false → 空的 SLOTS 只会写出一个空 tag，客户端 readFromSyncNbt
        //                    什么都不做，等于没复位。
        // 注意 sync() 是在 KEY.sync() 之后才把 fullSync 置回 false，所以这里读到的
        // 仍是本次发包用的那个值。
        this.syncGate.recordSent(recipient, this.SLOTS.isEmpty() && this.fullSync);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
        if (!SAVE_FLAG && !syncMode) {
            return;
        }
        if (fullSync)
            tag.putBoolean("remove_all", true);
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
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        // 没有槽位数据时只同步给本人：CCA 在开始追踪 / 玩家登录时会对每个组件无脑 sync，
        // 本组件挂在所有玩家身上，不门控就是「玩家数 × 观察者数」个空包。
        return this.syncGate.shouldSyncWith(sp, isDefaultSyncState());
    }

    /** 是否处于「没有任何槽位数据」的默认态（此时空包对观察者没有任何意义）。 */
    public boolean isDefaultSyncState() {
        return this.SLOTS.isEmpty() && !this.fullSync;
    }

    public Player getPlayer() {
        return this.player;
    }

    public ItemStack hurtAndBreak(ItemStack stack, int damage, ResourceLocation slot) {
        if (this.player.level().isClientSide)
            return ItemStack.EMPTY;
        if (!(this.player instanceof ServerPlayer serverPlayer)) {
            return ItemStack.EMPTY;
        }
        ServerLevel serverLevel = serverPlayer.serverLevel();
        if (stack.isDamageableItem()) {
            if (!player.hasInfiniteMaterials()) {
                if (damage > 0) {
                    damage = EnchantmentHelper.processDurabilityChange(serverLevel, stack, damage);
                    if (damage <= 0) {
                        return ItemStack.EMPTY;
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
        return stack;
    }

    public void clear() {
        // 本来就没有任何槽位数据、也没有观察者拿到过数据时，这次 sync 只会给所有人
        // 发一个 remove_all 空包（开局 / 局末对每个玩家都会 clear 一次 → N² 个包），
        // 直接跳过：客户端本来就是空槽位态。
        if (this.SLOTS.isEmpty() && !this.syncGate.hasSyncedViewers()) {
            this.fullSync = false;
            this.syncGate.clear();
            return;
        }
        this.SLOTS.clear();
        this.fullSync = true;
        sync();
        // sync() 发出的是 remove_all，观察者客户端已被整体复位，
        // 不再需要 DefaultStateSyncGate 给他们补发空包。
        this.syncGate.clear();
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
        needSync.clear();
        fullSync = false;
    }

    public boolean hasItemStack(ItemStack itemStack) {
        return this.SLOTS.containsValue(itemStack);
    }

    public ItemStack removeSlot(@NotNull ResourceLocation slot) {
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

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}

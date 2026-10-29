package org.agmas.noellesroles.game.halloween.pumpkin;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 临时南瓜头事务：原栈只暂存一份，不把装备复制进可移动物品。 */
public final class PumpkinHeads {
    private static final String TOKEN = "noellesroles_pumpkin_head";
    private static final Map<UUID, Entry> LEASES = new HashMap<>();
    private record Entry(ServerPlayer player, ServerLevel level, SRERole role, UUID token,
                         HeadLease<ItemStack> lease) {}
    private PumpkinHeads() {}

    public static void apply(ServerPlayer player, int ticks) {
        long until = PumpkinSpiritHandler.now(player.serverLevel()) + ticks;
        Entry old = LEASES.get(player.getUUID());
        if (old != null) {
            if (old.player == player && old.level == player.serverLevel()) {
                old.lease.extend(until);
                return;
            }
            restore(old.player);
        }
        ItemStack original = player.getItemBySlot(EquipmentSlot.HEAD);
        UUID token = UUID.randomUUID();
        ItemStack head = new ItemStack(Items.CARVED_PUMPKIN);
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TOKEN, token);
        head.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        LEASES.put(player.getUUID(), new Entry(player, player.serverLevel(), RoleUtils.getPlayerRole(player),
                token, new HeadLease<>(original, until)));
        player.setItemSlot(EquipmentSlot.HEAD, head);
        player.containerMenu.broadcastFullState();
    }

    public static boolean temporary(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().hasUUID(TOKEN);
    }

    public static boolean locked(Player player) { return LEASES.containsKey(player.getUUID()); }

    /** 点击头槽/光标上的临时头一律拒绝；不锁其他正常背包操作。 */
    public static boolean blocksClick(AbstractContainerMenu menu, int slotId, Player player) {
        if (temporary(menu.getCarried())) return true;
        if (slotId < 0 || slotId >= menu.slots.size()) return false;
        Slot slot = menu.getSlot(slotId);
        return temporary(slot.getItem()) || locked(player)
                && slot.container == player.getInventory() && slot.getContainerSlot() == 39;
    }

    public static boolean blocksDrag(Slot slot) {
        return temporary(slot.getItem());
    }

    public static void tick(ServerLevel level) {
        for (Entry e : new ArrayList<>(LEASES.values())) {
            if (e.level != level) continue;
            if (e.player.serverLevel() != level || !PumpkinSpiritHandler.alive(e.player)
                    || e.role != RoleUtils.getPlayerRole(e.player) || e.lease.expired(PumpkinSpiritHandler.now(level))) {
                restore(e.player);
            }
        }
    }

    public static void restore(ServerPlayer player) {
        Entry e = LEASES.remove(player.getUUID());
        if (e == null) return;
        ItemStack original = e.lease.release();
        if (original == null) return;
        ItemStack current = player.getItemBySlot(EquipmentSlot.HEAD);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        // 管理员/其他 API 强行替换了头槽，也不能吞掉那件新物品。
        if (!current.isEmpty() && !temporary(current)) PumpkinSpiritHandler.giveRaw(player, current);
        // 清理非法移出的临时 token；原装备始终只存在于租约内。
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (temporary(player.getInventory().getItem(i))) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        if (temporary(player.containerMenu.getCarried())) player.containerMenu.setCarried(ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.HEAD, original);
        player.containerMenu.broadcastFullState();
    }

    static void clear(ServerLevel level) {
        for (Entry e : new ArrayList<>(LEASES.values())) if (e.level == level) restore(e.player);
    }
}

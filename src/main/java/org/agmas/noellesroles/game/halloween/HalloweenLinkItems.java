package org.agmas.noellesroles.game.halloween;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** 发起者物品的实时借用视图；不发奖、不复制收益、不回写伙伴自行获得的物品。 */
public final class HalloweenLinkItems {
    private static final ThreadLocal<ServerPlayer> USING = new ThreadLocal<>();
    private HalloweenLinkItems() {}

    /** 槽位共享真实 ItemStack，shrink、耐久和组件变化直接作用于发起者。 */
    static void sync(ServerPlayer owner, ServerPlayer borrower) {
        if (owner.serverLevel() != borrower.serverLevel()
                || !GameUtils.isPlayerAliveAndSurvival(owner) || !GameUtils.isPlayerAliveAndSurvival(borrower)) return;
        Inventory source = owner.getInventory(), target = borrower.getInventory();
        boolean changed = false;
        for (int group = 0; group < source.compartments.size(); group++) {
            changed |= HalloweenLinkRules.mirrorSlots(source.compartments.get(group), target.compartments.get(group));
        }
        if (!borrower.containerMenu.getCarried().isEmpty()) {
            borrower.containerMenu.setCarried(ItemStack.EMPTY);
            changed = true;
        }
        if (changed) target.setChanged();
        owner.containerMenu.broadcastChanges();
        borrower.containerMenu.broadcastChanges();
    }

    private static void set(Inventory inventory, int slot, ItemStack stack) {
        for (var compartment : inventory.compartments) {
            if (slot < compartment.size()) { compartment.set(slot, stack); return; }
            slot -= compartment.size();
        }
    }

    /** 仅分离伙伴槽位，不能 clear() 共享物品栈，否则会删除发起者的物品。 */
    static void clear(ServerPlayer borrower) {
        for (var compartment : borrower.getInventory().compartments)
            HalloweenLinkRules.detachSlots(compartment, ItemStack.EMPTY);
        borrower.containerMenu.setCarried(ItemStack.EMPTY);
        borrower.getInventory().setChanged();
        borrower.containerMenu.broadcastFullState();
    }

    /** 仅真实物品使用期间允许空瓶等替换结果写回，普通发奖不接受。 */
    public static void use(ServerPlayer player, Runnable action) {
        HalloweenSoulLinks.syncItems(player);
        if (!HalloweenSoulLinks.isItemBorrower(player)) {
            try { action.run(); } finally { HalloweenSoulLinks.syncItems(player); }
            return;
        }
        ServerPlayer previous = USING.get();
        USING.set(player);
        try { action.run(); }
        finally {
            if (previous == null) USING.remove(); else USING.set(previous);
            HalloweenSoulLinks.syncItems(player);
        }
    }

    public static boolean replaceEquipment(ServerPlayer player, EquipmentSlot slot, ItemStack replacement) {
        int index = switch (slot) {
            case MAINHAND -> player.getInventory().selected;
            case OFFHAND -> 40;
            case FEET -> 36;
            case LEGS -> 37;
            case CHEST -> 38;
            case HEAD -> 39;
            default -> -1;
        };
        return replaceConsumedSlot(player.getInventory(), index, replacement);
    }

    /** 新槽位替换只允许消费完成后的容器转换（如药水瓶），不接受伙伴的新奖励回写。 */
    public static boolean replaceConsumedSlot(Inventory inventory, int slot, ItemStack replacement) {
        if (!(inventory.player instanceof ServerPlayer borrower)) return false;
        ServerPlayer owner = HalloweenSoulLinks.itemOwner(borrower);
        if (owner == null || slot < 0 || slot >= inventory.getContainerSize()) return false;
        ItemStack old = inventory.getItem(slot);
        if (old == owner.getInventory().getItem(slot)
                && (USING.get() == borrower || replacement.isEmpty() && !old.isEmpty())) {
            set(owner.getInventory(), slot, replacement);
        }
        sync(owner, borrower);
        return true;
    }
}

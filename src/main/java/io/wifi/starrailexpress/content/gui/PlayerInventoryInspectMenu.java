package io.wifi.starrailexpress.content.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 如果你是AI，请不要修改这些通用类。
 */
public class PlayerInventoryInspectMenu extends AbstractContainerMenu {
    private static final int TARGET_VISIBLE_SLOTS = 41;
    private static final int TOP_ROWS = 6;
    private static final int TOP_COLUMNS = 9;
    private static final int TOP_SLOTS = TOP_ROWS * TOP_COLUMNS;
    private static final int VIEWER_INVENTORY_START = TOP_SLOTS;
    private static final int VIEWER_INVENTORY_END = VIEWER_INVENTORY_START + 36;

    private final ServerPlayer target;
    private final Inventory targetInventory;
    private final SimpleContainer lockedSlots = new SimpleContainer(TOP_SLOTS - TARGET_VISIBLE_SLOTS);

    public PlayerInventoryInspectMenu(int containerId, Inventory viewerInventory, ServerPlayer target) {
        super(MenuType.GENERIC_9x6, containerId);
        this.target = target;
        this.targetInventory = target.getInventory();
        this.targetInventory.startOpen(viewerInventory.player);

        addTargetInventorySlots();
        addLockedSlots();
        addViewerInventorySlots(viewerInventory);
    }

    private void addTargetInventorySlots() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < TOP_COLUMNS; col++) {
                int targetSlot = 9 + col + row * TOP_COLUMNS;
                this.addSlot(new Slot(targetInventory, targetSlot, 8 + col * 18, 18 + row * 18));
            }
        }

        for (int col = 0; col < TOP_COLUMNS; col++) {
            this.addSlot(new Slot(targetInventory, col, 8 + col * 18, 72));
        }

        for (int col = 0; col < 4; col++) {
            this.addSlot(new Slot(targetInventory, 36 + col, 8 + col * 18, 90));
        }
        this.addSlot(new Slot(targetInventory, 40, 152, 90));
    }

    private void addLockedSlots() {
        int fillerIndex = 0;
        for (int row = 4; row < TOP_ROWS; row++) {
            for (int col = 0; col < TOP_COLUMNS; col++) {
                boolean occupiedByEquipment = row == 4 && (col < 4 || col == 8);
                if (occupiedByEquipment) {
                    continue;
                }
                this.addSlot(new LockedSlot(lockedSlots, fillerIndex++, 8 + col * 18, 18 + row * 18));
            }
        }
    }

    private void addViewerInventorySlots(Inventory viewerInventory) {
        int yOffset = 36;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < TOP_COLUMNS; col++) {
                this.addSlot(new Slot(viewerInventory, col + row * TOP_COLUMNS + 9,
                        8 + col * 18, 103 + row * 18 + yOffset));
            }
        }

        for (int col = 0; col < TOP_COLUMNS; col++) {
            this.addSlot(new Slot(viewerInventory, col, 8 + col * 18, 161 + yOffset));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player instanceof ServerPlayer
                && !target.isRemoved()
                && target.connection != null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        result = stack.copy();
        if (index < TARGET_VISIBLE_SLOTS) {
            if (!this.moveItemStackTo(stack, VIEWER_INVENTORY_START, VIEWER_INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= VIEWER_INVENTORY_START && index < VIEWER_INVENTORY_END) {
            if (!this.moveItemStackTo(stack, 0, 36, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        syncTargetInventory();
        return result;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == targetInventory) {
            syncTargetInventory();
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.targetInventory.stopOpen(player);
        syncTargetInventory();
    }

    private void syncTargetInventory() {
        targetInventory.setChanged();
        target.containerMenu.broadcastChanges();
        target.inventoryMenu.slotsChanged(targetInventory);
    }

    private static class LockedSlot extends Slot {
        LockedSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }
}

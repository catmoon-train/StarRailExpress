package org.agmas.noellesroles.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModMenus;

public class HotbarStorageMenu extends AbstractContainerMenu {
    private static final int CONTAINER_ROWS = 3;
    private static final int CONTAINER_COLUMNS = 9;
    private static final int CONTAINER_SIZE = CONTAINER_ROWS * CONTAINER_COLUMNS;

    private final Container container;
    private final BlockPos pos;

    public HotbarStorageMenu(int syncId, Inventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, new SimpleContainer(CONTAINER_SIZE), pos);
    }

    public HotbarStorageMenu(int syncId, Inventory playerInventory, Container container, BlockPos pos) {
        super(ModMenus.HOTBAR_STORAGE, syncId);
        checkContainerSize(container, CONTAINER_SIZE);
        this.container = container;
        this.pos = pos;
        container.startOpen(playerInventory.player);

        for (int row = 0; row < CONTAINER_ROWS; row++) {
            for (int col = 0; col < CONTAINER_COLUMNS; col++) {
                this.addSlot(new Slot(container, col + row * CONTAINER_COLUMNS, 8 + col * 18, 18 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 79));
        }
    }

    public BlockPos getPos() {
        return this.pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
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
        if (index < CONTAINER_SIZE) {
            if (!this.moveItemStackTo(stack, CONTAINER_SIZE, CONTAINER_SIZE + 9, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, CONTAINER_SIZE, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}

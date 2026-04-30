package io.wifi.starrailexpress.content.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class PlayerBodyChestMenu extends AbstractContainerMenu implements CustomInventoryMenu {
    private final BodyEntityContainer container;
    private final int rows;

    // 如果你使用原版 MenuType，可在此替换为你的自定义类型或直接用原版
    public PlayerBodyChestMenu(int containerId, Inventory playerInventory, BodyEntityContainer container) {
        super(MenuType.GENERIC_9x3, containerId);
        this.container = container;
        this.rows = 3;
        container.startOpen(playerInventory.player);

        // 容器槽位（3行 x 9列）
        int i = (rows - 4) * 18; // 与 ChestMenu 对齐
        for (int row = 0; row < rows; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }

        // 玩家背包（27格）
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + i));
            }
        }

        // 玩家快捷栏（9格）
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 161 + i));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    // 完全禁止 Shift+点击移动物品（即使有权限也可在此统一拦截）
    // @Override
    // public ItemStack quickMoveStack(Player player, int index) {
    //     return ItemStack.EMPTY; // 禁止任何快速移动
    // }

    // 可选：如果还想保留部分 Shift 功能（仅对有权限玩家开放），可以写：
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!container.canGetBodyContent(player)) return ItemStack.EMPTY;
        // 实现正常快速移动逻辑（复制自 ChestMenu），但通常没必要
        if (!container.canGetBodyContent(player)) {
            return ItemStack.EMPTY;
        }

        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemStack = slotStack.copy();

            if (index < this.rows * 9) {
                // 从容器槽位移到玩家背包
                if (!this.moveItemStackTo(slotStack, this.rows * 9, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 从玩家背包移到容器（受容器 canPlaceItem 限制，此处始终 false，会失败）
                if (!this.moveItemStackTo(slotStack, 0, this.rows * 9, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemStack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    // 提供外部获取容器（如果你需要）
    public Container getContainer() {
        return container;
    }
}
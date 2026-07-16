package net.exmo.sre.sixtyseconds.content.mail;

import net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SixtySecondsMailboxContainer extends AbstractContainerMenu {

    private final SixtySecondsMailboxBlockEntity mailbox;

    public SixtySecondsMailboxContainer(int id, Inventory playerInv, SixtySecondsMailboxBlockEntity mailbox) {
        super(null, id);
        this.mailbox = mailbox;

        // 邮箱槽位：3x3（槽位 0-8）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(mailbox, row * 3 + col, 62 + col * 18, 17 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return mailbox.canPlaceItem(getSlotIndex(), stack);
                    }
                });
            }
        }

        // 玩家背包（槽位 9-35）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // 玩家快捷栏（槽位 36-44）
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (slotIndex < 9) {
            // 邮箱 → 玩家背包
            if (!moveItemStackTo(stack, 9, 45, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 玩家背包 → 邮箱（只允许稿纸）
            if (stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_DRAFT_PAPER)) {
                if (!moveItemStackTo(stack, 0, 9, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return mailbox.stillValid(player);
    }
}

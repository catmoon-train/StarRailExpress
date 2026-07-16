package net.exmo.sre.sixtyseconds.content.mail;

import net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SixtySecondsMailboxContainer extends AbstractContainerMenu {

    private final SixtySecondsMailboxBlockEntity mailbox;

    public SixtySecondsMailboxContainer(int id, Inventory playerInv, SixtySecondsMailboxBlockEntity mailbox) {
        super(null, id);
        this.mailbox = mailbox;

        // 邮箱槽位：6行 + 3行（槽位 0-26）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(mailbox, row * 9 + col, 8 + col * 18, 17 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return mailbox.canPlaceItem(getContainerSlot(), stack);
                    }
                });
            }
        }

        // 玩家背包（槽位 27-53）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // 玩家快捷栏（槽位 54-62）
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

        if (slotIndex < 27) {
            if (!moveItemStackTo(stack, 27, 63, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_DRAFT_PAPER)
                    || stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_COIN)
                    || stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_EXPRESS_PACKAGE)) {
                if (!moveItemStackTo(stack, 0, 27, false)) {
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

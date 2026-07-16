package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 背包（小 9 格 / 中 18 格 / 大 27 格）：右键打开，内容物存
 * {@link DataComponents#CONTAINER}（掉落/交易随物品走）。
 * 防套娃：背包不能装背包（占位屏障也不可放入）；打开中的背包自身不可被拿起。
 */
public class SixtySecondsBackpackItem extends Item {
    private final int rows;

    public SixtySecondsBackpackItem(Properties properties, int rows) {
        super(properties);
        this.rows = rows;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (!SixtySecondsMod.isActive(level) && !serverPlayer.isCreative()) {
            return InteractionResultHolder.pass(stack);
        }
        serverPlayer.openMenu(new SimpleMenuProvider(
                (syncId, inventory, p) -> new BackpackMenu(syncId, inventory, stack, rows),
                stack.getHoverName()));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 1.0F);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int used = (int) stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .nonEmptyStream().count();
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.backpack", used, rows * 9)
                .withStyle(ChatFormatting.GRAY));
    }

    /** 背包容器：每次变更即写回物品的 CONTAINER 组件（防崩服丢内容）。 */
    static class BackpackContainer extends SimpleContainer {
        private final ItemStack backpack;

        BackpackContainer(ItemStack backpack, int size) {
            super(size);
            this.backpack = backpack;
            backpack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(getItems());
        }

        @Override
        public void setChanged() {
            super.setChanged();
            backpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
        }
    }

    /** 背包菜单：复用原版箱子界面（GENERIC_9xN），但容器格禁放背包/屏障、背包自身格禁拿起。 */
    public static class BackpackMenu extends AbstractContainerMenu {
        private final Container container;
        private final ItemStack backpackStack;
        private final int containerSlots;

        public BackpackMenu(int syncId, Inventory playerInventory, ItemStack backpackStack, int rows) {
            super(menuType(rows), syncId);
            this.backpackStack = backpackStack;
            this.containerSlots = rows * 9;
            this.container = new BackpackContainer(backpackStack, containerSlots);
            container.startOpen(playerInventory.player);
            int yOffset = (rows - 4) * 18;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            // 防套娃/防占位屏障入包
                            return !(stack.getItem() instanceof SixtySecondsBackpackItem)
                                    && !stack.is(Items.BARRIER);
                        }
                    });
                }
            }
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new PlayerSlot(playerInventory, col + row * 9 + 9,
                            8 + col * 18, 103 + row * 18 + yOffset));
                }
            }
            for (int col = 0; col < 9; col++) {
                addSlot(new PlayerSlot(playerInventory, col, 8 + col * 18, 161 + yOffset));
            }
        }

        private static MenuType<?> menuType(int rows) {
            return switch (rows) {
                case 1 -> MenuType.GENERIC_9x1;
                case 2 -> MenuType.GENERIC_9x2;
                case 4 -> MenuType.GENERIC_9x4; // 军用背包 36 格
                case 5 -> MenuType.GENERIC_9x5;
                case 6 -> MenuType.GENERIC_9x6; // 商旅背包 54 格
                default -> MenuType.GENERIC_9x3;
            };
        }

        /** 玩家背包格：打开中的背包自身不可被拿起/移动。 */
        private class PlayerSlot extends Slot {
            PlayerSlot(Inventory inventory, int index, int x, int y) {
                super(inventory, index, x, y);
            }

            @Override
            public boolean mayPickup(Player player) {
                return getItem() != backpackStack && super.mayPickup(player);
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            ItemStack result = ItemStack.EMPTY;
            Slot slot = this.slots.get(index);
            if (slot.hasItem()) {
                ItemStack stack = slot.getItem();
                result = stack.copy();
                if (index < containerSlots) {
                    if (!moveItemStackTo(stack, containerSlots, this.slots.size(), true)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(stack, 0, containerSlots, false)) {
                    return ItemStack.EMPTY;
                }
                if (stack.isEmpty()) {
                    slot.setByPlayer(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
            }
            return result;
        }

        @Override
        public boolean stillValid(Player player) {
            // 背包被丢弃/清空后自动关闭
            return !backpackStack.isEmpty() && player.getInventory().contains(backpackStack)
                    || player.getOffhandItem() == backpackStack;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            container.stopOpen(player);
        }
    }
}

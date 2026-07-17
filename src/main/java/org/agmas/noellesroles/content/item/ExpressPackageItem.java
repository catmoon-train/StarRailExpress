package org.agmas.noellesroles.content.item;

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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 快递包裹 — 1格容器，右键打开，使用 DataComponents.CONTAINER 存储。
 * 可放入邮箱，被快递员取走配送。
 */
public class ExpressPackageItem extends Item {

    public ExpressPackageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        serverPlayer.openMenu(new SimpleMenuProvider(
                (syncId, inventory, p) -> new PackageMenu(syncId, inventory, stack),
                stack.getHoverName()));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 1.0F);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds_express_package"));
        boolean hasContent = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .nonEmptyStream().findAny().isPresent();
        if (hasContent) {
            tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds_express_package.filled")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds_express_package.empty")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** 1格容器：每次变更写回物品的 CONTAINER 组件 */
    static class PackageContainer extends SimpleContainer {
        private final ItemStack packageStack;

        PackageContainer(ItemStack packageStack) {
            super(1);
            this.packageStack = packageStack;
            packageStack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(getItems());
        }

        @Override
        public void setChanged() {
            super.setChanged();
            packageStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
        }
    }

    /** 快递包裹菜单：1格容器，仅第一个槽位可用 */
    public static class PackageMenu extends AbstractContainerMenu {
        private final Container container;
        private final ItemStack packageStack;

        public PackageMenu(int syncId, Inventory playerInventory, ItemStack packageStack) {
            super(MenuType.GENERIC_9x1, syncId);
            this.packageStack = packageStack;
            this.container = new PackageContainer(packageStack);
            container.startOpen(playerInventory.player);

            // 第1格（可用）
            addSlot(new Slot(container, 0, 8 + 4 * 18, 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return true;
                }
            });
            // 其余8格禁用
            for (int col = 0; col < 8; col++) {
                int idx = col;
                addSlot(new Slot(container, idx + 1, -1000, -1000) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return false;
                    }
                });
            }

            // 玩家背包
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new PlayerSlot(playerInventory, col + row * 9 + 9,
                            8 + col * 18, 84 + row * 18));
                }
            }
            for (int col = 0; col < 9; col++) {
                addSlot(new PlayerSlot(playerInventory, col, 8 + col * 18, 142));
            }
        }

        private class PlayerSlot extends Slot {
            PlayerSlot(Inventory inventory, int index, int x, int y) {
                super(inventory, index, x, y);
            }

            @Override
            public boolean mayPickup(Player player) {
                return getItem() != packageStack && super.mayPickup(player);
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index == 0) {
                // 包裹格 → 玩家背包
                Slot slot = slots.get(index);
                if (slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    ItemStack result = stack.copy();
                    if (!moveItemStackTo(stack, 10, 46, true)) {
                        return ItemStack.EMPTY;
                    }
                    if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
                    else slot.setChanged();
                    return result;
                }
            } else if (index >= 10 && index < 46) {
                // 玩家背包 → 包裹格
                Slot slot = slots.get(index);
                if (slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    if (!moveItemStackTo(stack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return !packageStack.isEmpty() && player.getInventory().contains(packageStack)
                    || player.getOffhandItem() == packageStack;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            container.stopOpen(player);
        }
    }
}

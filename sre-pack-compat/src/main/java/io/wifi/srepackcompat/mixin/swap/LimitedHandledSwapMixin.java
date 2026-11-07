package io.wifi.srepackcompat.mixin.swap;

import io.wifi.srepackcompat.swap.LimitedSwapRules;
import io.wifi.srepackcompat.swap.LimitedSwapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** 复用限制背包自身的命中检测、缩放布局和取物限制，缺少核心时自动跳过。 */
@Pseudo
@Mixin(targets = "io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen", remap = false)
public abstract class LimitedHandledSwapMixin implements LimitedSwapScreen {
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected abstract boolean shouldRenderHotbarSlots();
    @Shadow protected abstract int getSlotRenderX(Slot slot);
    @Shadow protected abstract int getSlotRenderY(Slot slot);
    @Shadow protected abstract float getSlotRenderScale(Slot slot);
    @Shadow protected abstract boolean isItemTakeBlocked(ItemStack stack);
    @Shadow protected abstract void onMouseClick(Slot slot, int slotId, int button, ClickType type);
    @Shadow private Slot getSlotAt(double x, double y) { throw new AssertionError(); }
    @Shadow public static int getYOffset() { throw new AssertionError(); }

    @Override
    public Slot sreSwap$slotAt(double mouseX, double mouseY) {
        // 回声面板隐藏原槽位时，不允许通过原槽位坐标交换。
        return shouldRenderHotbarSlots() ? getSlotAt(mouseX, mouseY) : null;
    }

    @Override
    public boolean sreSwap$swap(Slot slot, int hotbar) {
        var player = Minecraft.getInstance().player;
        var menu = ((MenuAccess<?>) this).getMenu();
        if (player == null || !shouldRenderHotbarSlots() || !slot.isActive()
                || slot.container != player.getInventory() || !menu.slots.contains(slot)
                || !menu.getCarried().isEmpty()) return false;
        ItemStack hand = player.getInventory().getItem(hotbar);
        if (!LimitedSwapRules.canSwap(slot.index, hotbar, isItemTakeBlocked(slot.getItem()), isItemTakeBlocked(hand))
                || !slot.mayPickup(player) || (!hand.isEmpty() && !slot.mayPlace(hand))) return false;
        onMouseClick(slot, slot.index, hotbar, ClickType.SWAP);
        return true;
    }

    @Override
    public double[] sreSwap$cursorCorner() {
        if (!shouldRenderHotbarSlots()) return null;
        for (Slot slot : ((MenuAccess<?>) this).getMenu().slots) {
            if (slot.index == 44 && slot.isActive()) {
                int size = Math.round(16 * getSlotRenderScale(slot));
                return new double[]{x + getSlotRenderX(slot) + size + 2,
                        y - getYOffset() + getSlotRenderY(slot) + size + 2};
            }
        }
        return null;
    }
}

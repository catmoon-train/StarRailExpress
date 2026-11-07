package io.wifi.srepackcompat.swap;

/** 限制背包仅允许可见快捷栏之间交换，且交换双方都必须允许移动。 */
public final class LimitedSwapRules {
    private LimitedSwapRules() { }

    public static boolean canSwap(int menuSlot, int selectedHotbar, boolean targetBlocked, boolean handBlocked) {
        return menuSlot >= 36 && menuSlot <= 44 && selectedHotbar >= 0 && selectedHotbar < 9
                && menuSlot != 36 + selectedHotbar && !targetBlocked && !handBlocked;
    }
}

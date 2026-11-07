package io.wifi.srepackcompat.swap;

import net.minecraft.world.inventory.Slot;

/** 可选的限制背包桥接，不引用 SRE 类，保持兼容包可独立加载。 */
public interface LimitedSwapScreen {
    Slot sreSwap$slotAt(double x, double y);
    boolean sreSwap$swap(Slot slot, int hotbar);
    /** 返回实际可见快捷栏的右下角（GUI 坐标）；无可见槽位时返回 null。 */
    double[] sreSwap$cursorCorner();
}

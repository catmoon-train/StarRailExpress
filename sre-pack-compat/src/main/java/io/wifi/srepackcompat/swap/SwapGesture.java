package io.wifi.srepackcompat.swap;

/** 背包键的一次按住手势；与游戏对象解耦，便于验证时间边界。 */
public final class SwapGesture {
    private boolean held;
    private long started;

    public void press(long now) {
        if (!held) {
            held = true;
            started = now;
        }
    }

    public boolean isHeld() { return held; }

    public boolean release(long now, int thresholdMs) {
        boolean swap = held && now - started >= (long) thresholdMs * 1_000_000L;
        reset();
        return swap;
    }

    public void reset() { held = false; }

    public static int inventoryMenuSlot(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex >= 36) {
            throw new IllegalArgumentException("非主背包槽位: " + inventoryIndex);
        }
        return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
    }
}

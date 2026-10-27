package org.agmas.noellesroles.game.modes.echo.inventory;

/** 纯槽位策略：前三格固定工具，后六格仅是唯一库存的只读投影。 */
public final class EchoHotbarSlots {
    public static final int TOOLS = 3, RESOURCES = 6, TOTAL = TOOLS + RESOURCES;
    public enum Click { PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL }
    private EchoHotbarSlots() { }
    public static boolean protectedSlot(int inventorySlot) { return inventorySlot >= 0 && inventorySlot < TOTAL; }
    public static int resourceSlot(int inventorySlot) { return inventorySlot >= TOOLS && inventorySlot < TOTAL ? inventorySlot - TOOLS : -1; }
    public static int inventorySlot(int resourceSlot) {
        if (resourceSlot < 0 || resourceSlot >= RESOURCES) throw new IllegalArgumentException("物资格必须在 0..5");
        return TOOLS + resourceSlot;
    }
    /** 跨槽收集、快捷移动和拖拽不能间接写入固定栏；空工具槽同样保护。 */
    public static boolean blockClick(boolean active, int inventorySlot, int button, Click click, boolean projection) {
        if (!active) return false;
        if (projection || protectedSlot(inventorySlot)) return true;
        return switch (click) {
            case QUICK_MOVE, CLONE, QUICK_CRAFT, PICKUP_ALL -> true;
            case SWAP -> protectedSlot(button) || button == 40;
            default -> false;
        };
    }
}

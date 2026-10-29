package org.agmas.noellesroles.game.modes.echo.client;

/** 原生背包回声页的纯几何布局；三工具与六物资共用快捷栏编号。 */
public record EchoInventoryLayout(int x, int y, int width, int height, int slotSize, int slotStep, int toolsX, int resourcesX) {
    public static EchoInventoryLayout of(int screenWidth, int screenHeight) {
        int width = Math.min(700, screenWidth - 16);
        int height = Math.min(380, screenHeight - 16);
        int step = Math.min(36, (width - 32 - 14) / 9);
        int size = step - 3;
        int total = 9 * step - 3 + 14;
        int x = (screenWidth - width) / 2, y = (screenHeight - height) / 2;
        int toolsX = x + (width - total) / 2;
        return new EchoInventoryLayout(x, y, width, height, size, step, toolsX, toolsX + 3 * step + 14);
    }
    public int slotX(int hotbarSlot) { return hotbarSlot < 3 ? toolsX + hotbarSlot * slotStep : resourcesX + (hotbarSlot - 3) * slotStep; }
    public int slotY() { return y + height - slotSize - 19; }
    public int labelY() { return slotY() - 12; }
    public int contentY() { return y + 66; }
    public int actionsY() { return labelY() - 38; }
    public int detailBottom() { return actionsY() - 6; }
    public int hintY() { return labelY() - 16; }
    public int choiceWidth() { return (width - 24) / 3; }
    public int choiceX(int choice) { return x + 8 + choice * (choiceWidth() + 4); }
    public int modalWidth() { return Math.min(360, width - 24); }
    public int modalHeight() { return 156; }
    public int modalX() { return x + (width - modalWidth()) / 2; }
    public int modalY() { return y + (height - modalHeight()) / 2; }
    public int modalButtonY() { return modalY() + modalHeight() - 28; }
    public int choiceTop() { return y + 49; }
    public int choiceBottom() { return actionsY() + 21; }
}

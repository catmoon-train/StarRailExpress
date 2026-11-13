package org.agmas.noellesroles.client.roulette;

/** 弹壳条固定在右侧上三分之一，不覆盖居中的准星及下方操作面板。 */
public record RouletteAmmoLayout(int x, int y, int width, int height) {
    public static final int ICON_WIDTH = 12, ICON_HEIGHT = 30, STEP = 15;
    public static RouletteAmmoLayout right(int screenWidth, int screenHeight, int shells) {
        int width = Math.min(Math.max(0, screenWidth - 16), Math.max(78, 13 + Math.clamp(shells, 0, 8) * STEP));
        int height = 55;
        int y = Math.max(8, Math.min(screenHeight - height - 8, screenHeight * 3 / 10 - height / 2));
        return new RouletteAmmoLayout(Math.max(8, screenWidth - width - 10), y, width, height);
    }
}

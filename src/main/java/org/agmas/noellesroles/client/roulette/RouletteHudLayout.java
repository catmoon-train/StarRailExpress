package org.agmas.noellesroles.client.roulette;

/** 下方仅保留回合与生命，状态、操作提示和私密情报各占至多一行。 */
public record RouletteHudLayout(int x, int y, int width, int height) {
    public static RouletteHudLayout bottom(int screenWidth, int screenHeight, int extraRows) {
        int width = Math.max(0, Math.min(300, screenWidth - 16));
        int height = Math.min(Math.max(0, screenHeight - 16), 35 + Math.clamp(extraRows, 0, 3) * 12);
        return new RouletteHudLayout((screenWidth - width) / 2, Math.max(8, screenHeight - 44 - height), width, height);
    }
}

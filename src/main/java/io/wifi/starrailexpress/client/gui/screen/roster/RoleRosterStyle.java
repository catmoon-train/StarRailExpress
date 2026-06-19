package io.wifi.starrailexpress.client.gui.screen.roster;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * 复用 exmo 标题界面风格的渲染辅助：暖色面板、金色描边、渐变背景与缓动 / 颜色插值。
 */
final class RoleRosterStyle {
    // 颜色（与 StarRailExpressTitleScreen 保持一致的暖色调）
    static final int PANEL_BG = 0x5A1A1008;
    static final int PANEL_BORDER = 0xAA8B6914;
    static final int ROW_BG = 0x52120A04;
    static final int ROW_BG_HOVER = 0x7A1A1008;
    static final int ROW_BORDER = 0xA020140A;
    static final int TITLE = 0xE8D8B0;
    static final int SUBTITLE = 0x9E8B6E;
    static final int TEXT = 0xE8D5A8;
    static final int TEXT_HOVER = 0xFFF4DC;
    static final int ACCENT = 0xC9A84C;
    static final int ACCENT_HOVER = 0xD4AF37;
    static final int MUTED = 0xC8B898;
    static final int ENABLED_GREEN = 0x8FE0A0;
    static final int DISABLED_RED = 0xE08F8F;

    private RoleRosterStyle() {
    }

    /** 暖色渐变背景叠加，铺在原版模糊背景之上。 */
    static void renderBackdrop(GuiGraphics g, int width, int height) {
        g.fillGradient(0, 0, width, height, 0x66000000, 0xAA000010);
        g.fillGradient(0, height / 4, width, height, 0x00000000, 0x228B6914);
    }

    /** 带四边描边的面板。 */
    static void drawPanel(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
    }

    static float easeOutCubic(float t) {
        float f = 1.0F - t;
        return 1.0F - f * f * f;
    }

    static int lerpColor(float t, int a, int b) {
        t = Mth.clamp(t, 0, 1);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) Mth.lerp(t, ar, br) << 16)
                | ((int) Mth.lerp(t, ag, bg) << 8)
                | (int) Mth.lerp(t, ab, bb);
    }
}

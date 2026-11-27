/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.gui.screen.roster;

import io.wifi.starrailexpress.client.ui.SRETheme;
import io.wifi.starrailexpress.client.ui.UiSurface;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * 名单界面（职业轮选 / 预览 / 随机）共用的渲染辅助：黄铜 HUD 的面板、行底、缓动与颜色插值。
 *
 * <p>
 * 颜色全部取 {@link SRETheme}，面板画法交给 {@link UiSurface}（1px 圆角暗铜描边 + 微圆角），
 * 与设置中心、编辑器是同一套观感；本类只剩「名单这几个界面自己需要的半透明叠加」。
 */
final class RoleRosterStyle {
    // 颜色（黄铜 HUD，见 docs/ui_style.md §2）
    static final int PANEL_BG = SRETheme.withAlpha(SRETheme.SURFACE_TOP, 0xE6);
    static final int PANEL_BORDER = SRETheme.BORDER;
    static final int ROW_BG = SRETheme.withAlpha(SRETheme.SURFACE_BOTTOM, 0x8C);
    static final int ROW_BG_HOVER = SRETheme.withAlpha(SRETheme.SURFACE_BOTTOM_ALT, 0xE6);
    static final int ROW_BORDER = SRETheme.CARD_BORDER;
    static final int TITLE = SRETheme.TITLE;
    static final int SUBTITLE = SRETheme.MUTED;
    static final int TEXT = SRETheme.TEXT;
    static final int TEXT_HOVER = SRETheme.BRASS_BRIGHT;
    static final int ACCENT = SRETheme.GOLD_DIM;
    static final int ACCENT_HOVER = SRETheme.GOLD;
    static final int MUTED = SRETheme.BODY;
    static final int ENABLED_GREEN = SRETheme.GREEN;
    static final int DISABLED_RED = SRETheme.RED;

    private RoleRosterStyle() {
    }

    /** 全屏底：原版模糊背景之上压一层深灰黑，名单界面照旧能看见身后的场景。 */
    static void renderBackdrop(GuiGraphics g, int width, int height) {
        g.fillGradient(0, 0, width, height,
                SRETheme.withAlpha(SRETheme.SURFACE_TOP, 0x66), SRETheme.withAlpha(SRETheme.SURFACE_TOP, 0xB3));
    }

    /** 面板：纯色底 + 1px 圆角暗铜描边（唯一实现在 {@link UiSurface}）。 */
    static void drawPanel(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
        UiSurface.card(g, x, y, w, h, bg, border);
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

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

package io.wifi.starrailexpress.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * 任务点小游戏共用的绘制工具：圆角面板、阴影、圆形、进度条、缓动等。
 * 统一所有小游戏的视觉风格，避免在各个 Screen 中重复实现绘制逻辑。
 */
public final class MinigameUI {

    private MinigameUI() {
    }

    // 统一配色
    public static final int WHITE = 0xFFE8EEF8;
    public static final int MUTED = 0xFF9AA8BE;
    public static final int GREEN = 0xFF4ACB73;
    public static final int RED = 0xFFFF6B6B;
    public static final int YELLOW = 0xFFFFD166;
    public static final int BLUE = 0xFF4A8BFF;
    public static final int PANEL = 0xF023324A;
    public static final int PANEL_DARK = 0xFF131C2B;
    public static final int HEADER = 0xFF45689A;
    public static final int HEADER_LO = 0xFF2D456A;

    /** 屏幕暗化背景（带轻微竖直渐变）。 */
    public static void dim(GuiGraphics g, int width, int height) {
        g.fillGradient(0, 0, width, height, 0xD00B0E15, 0xD0121B33);
    }

    /** 圆角实心矩形（通过逐行内缩近似圆角）。 */
    public static void roundRect(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int color) {
        int h = y2 - y1;
        if (r <= 0 || h <= 0) {
            g.fill(x1, y1, x2, y2, color);
            return;
        }
        r = Math.min(r, h / 2);
        for (int row = 0; row < h; row++) {
            int edge = Math.min(row, h - 1 - row);
            int inset = 0;
            if (edge < r) {
                int d = r - edge;
                inset = r - (int) Math.floor(Math.sqrt((double) r * r - (double) d * d));
            }
            g.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, color);
        }
    }

    /** 圆角描边（仅绘制周边一圈，不覆盖内部）。 */
    public static void roundBorder(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int thickness, int color) {
        int h = y2 - y1;
        if (h <= 0) return;
        r = Math.min(r, h / 2);
        for (int row = 0; row < h; row++) {
            int edge = Math.min(row, h - 1 - row);
            int inset = 0;
            if (edge < r) {
                int d = r - edge;
                inset = r - (int) Math.floor(Math.sqrt((double) r * r - (double) d * d));
            }
            int yy = y1 + row;
            if (edge < thickness) {
                g.fill(x1 + inset, yy, x2 - inset, yy + 1, color);
            } else {
                g.fill(x1 + inset, yy, x1 + inset + thickness, yy + 1, color);
                g.fill(x2 - inset - thickness, yy, x2 - inset, yy + 1, color);
            }
        }
    }

    /** 面板柔和投影。 */
    public static void shadow(GuiGraphics g, int x1, int y1, int x2, int y2, int r) {
        for (int i = 5; i >= 1; i--) {
            int a = 0x07 + i * 4;
            roundRect(g, x1 - i, y1 + i + 1, x2 + i, y2 + i + 2, r + i, (a << 24));
        }
    }

    /** 绘制带标题栏的圆角面板，返回标题栏高度。 */
    public static int panel(GuiGraphics g, int x1, int y1, int x2, int y2, int header) {
        shadow(g, x1, y1, x2, y2, 10);
        roundRect(g, x1, y1, x2, y2, 10, PANEL);
        roundBorder(g, x1, y1, x2, y2, 10, 1, 0x40FFFFFF);
        // 顶部标题栏：圆角顶部 + 竖直渐变
        for (int row = 0; row < header; row++) {
            int inset = row < 10 ? 10 - (int) Math.floor(Math.sqrt(100.0 - (10 - row) * (10 - row))) : 0;
            g.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, lerpColor(HEADER, HEADER_LO, row / (float) header));
        }
        return header;
    }

    public static void filledCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int y = -r; y <= r; y++) {
            int half = (int) Math.sqrt((double) r * r - (double) y * y);
            g.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, color);
        }
    }

    public static void ring(GuiGraphics g, int cx, int cy, int r, int thickness, int color) {
        for (int y = -r; y <= r; y++) {
            int outer = (int) Math.sqrt((double) r * r - (double) y * y);
            int innerR = Math.max(0, r - thickness);
            int inner = Math.abs(y) <= innerR ? (int) Math.sqrt((double) innerR * innerR - (double) y * y) : -1;
            if (inner < 0) {
                g.fill(cx - outer, cy + y, cx + outer + 1, cy + y + 1, color);
            } else {
                g.fill(cx - outer, cy + y, cx - inner, cy + y + 1, color);
                g.fill(cx + inner + 1, cy + y, cx + outer + 1, cy + y + 1, color);
            }
        }
    }

    /** 圆角进度条。 */
    public static void progressBar(GuiGraphics g, int x, int y, int w, int h, float value, int color) {
        roundRect(g, x - 2, y - 2, x + w + 2, y + h + 2, h / 2 + 2, 0xFF3A4A60);
        roundRect(g, x, y, x + w, y + h, h / 2, PANEL_DARK);
        int fill = Math.round(w * Mth.clamp(value, 0f, 1f));
        if (fill > 2) {
            roundRect(g, x, y, x + fill, y + h, h / 2, color);
        }
    }

    public static float easeOut(float t) {
        t = Mth.clamp(t, 0f, 1f);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /** 0~1 之间的呼吸脉冲，speed 越大越快。 */
    public static float pulse(float time, float speed) {
        return 0.5f + 0.5f * (float) Math.sin(time * speed);
    }

    public static int withAlpha(int color, float alpha) {
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }
}

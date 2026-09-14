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

package io.wifi.starrailexpress.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

/**
 * {@code docs/ui_style.md} 指定的界面风格：复古列车 / 老式车票质感
 * ——深棕黑打底 + 米色文字 + 金色点缀。
 *
 * <p>
 * 面板一律按文档第 3 节的顺序绘制：上下渐变 → 棕褐描边 → 上边缘内侧一条浅米装饰线；
 * 颜色全部取自文档第 2 节的色板，不要在界面里再写死别的颜色。
 */
@Environment(EnvType.CLIENT)
public final class SREPanelStyle {

    // ==================== 基础色板（文档 2.1）====================

    /** 面板背景（上）。 */
    public static final int PANEL_BG_TOP = 0xD81A1008;
    /** 面板背景（下）。 */
    public static final int PANEL_BG_BOTTOM = 0xD820140A;
    /** 面板背景（下，偏蓝棕黑，做上下渐变用）。 */
    public static final int PANEL_BG_BOTTOM_ALT = 0xD80B1722;
    /** 全屏背景（上）。 */
    public static final int SCREEN_BG_TOP = 0xF018120A;
    /** 全屏背景（下）。 */
    public static final int SCREEN_BG_BOTTOM = 0xF0061018;
    /** 面板边框（棕褐）。 */
    public static final int BORDER = 0xFF8B6914;
    /** 顶部装饰线（半透明浅米）。 */
    public static final int TOP_LINE = 0x33FFE8C0;
    /** 亮金色（标题 / hover 边框 / 滚动条）。 */
    public static final int GOLD = 0xFFD4AF37;
    /** 棕金色（次强调）。 */
    public static final int GOLD_DIM = 0xFFC9A84C;
    /** 主文字（浅奶油）。 */
    public static final int TEXT = 0xFFFFF4DC;
    /** 标题文字（浅米）。 */
    public static final int TITLE = 0xFFF5E8C8;
    /** 次要文字（土褐）。 */
    public static final int MUTED = 0xFF9E8B6E;
    /** 暗米色正文。 */
    public static final int BODY = 0xFFC8B898;
    /** 功能蓝（计时 / 地图属性）。 */
    public static final int BLUE = 0xFF5EB7D8;
    /** 功能绿（确认 / 场景方块）。 */
    public static final int GREEN = 0xFF72C17B;
    /** 功能红（警告 / 错误）。 */
    public static final int RED = 0xFFE06B65;

    // ==================== 交互状态色（文档 2.3）====================

    /** hover 高亮背景。 */
    public static final int ROW_HOVER = 0x22FFFFFF;
    /** 行分隔线。 */
    public static final int ROW_SEPARATOR = 0x20FFFFFF;
    /** 非活跃卡片边框。 */
    public static final int CARD_BORDER = 0xFF5A4530;
    /** 选中 / 活跃行背景（上）。 */
    public static final int SELECTED_TOP = blendColors(0xFF1A1008, GOLD_DIM, 0.32F);
    /** 选中 / 活跃行背景（下）。 */
    public static final int SELECTED_BOTTOM = blendColors(0xFF120A04, GOLD_DIM, 0.18F);

    // ==================== 滚动条 ====================

    /** 滚动条宽（文档第 4 节：3~7px）。 */
    public static final int SCROLL_WIDTH = 7;
    /** 滚动条槽底色。 */
    public static final int SCROLL_TRACK = 0x66120A04;
    /** 滚动条槽内侧。 */
    public static final int SCROLL_TRACK_INNER = 0x33FFE8C0;
    /** 滚动条 thumb。 */
    public static final int SCROLL_THUMB = 0xFFB8912F;
    /** 滚动条 thumb 亮部。 */
    public static final int SCROLL_THUMB_LIGHT = 0xFFD4AF37;
    /** 滚动条 thumb hover。 */
    public static final int SCROLL_THUMB_HOVER = 0xFFD4AF37;
    /** 滚动条 thumb hover 亮部。 */
    public static final int SCROLL_THUMB_HOVER_LIGHT = 0xFFFFE8C0;

    // ==================== 面板绘制（文档第 3 节）====================

    /** 三步画一个面板：上下渐变 + 棕褐描边 + 上边缘内侧浅米装饰线。 */
    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, PANEL_BG_TOP, PANEL_BG_BOTTOM);
        g.renderOutline(x, y, w, h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, TOP_LINE);
    }

    /** 三步画一个面板（自定义上下背景色，用于需要偏蓝棕黑的分区）。 */
    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        g.fillGradient(x, y, x + w, y + h, top, bottom);
        g.renderOutline(x, y, w, h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, TOP_LINE);
    }

    /** 只画描边 + 上装饰线（用于已经画好背景的确认/取消这类页脚条）。 */
    public static void drawFooterLine(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, BORDER);
    }

    // ==================== 滚动条绘制 ====================

    /**
     * 画滚动条（槽 + thumb）。
     *
     * @param thumbY thumb 顶端（由调用方按自己的滚动几何算出）
     * @param thumbH thumb 高度（调用方保证 ≥ {@link #SCROLL_MIN_THUMB}）
     */
    public static void drawScrollbar(GuiGraphics g, int x, int y, int height, int thumbY, int thumbH, boolean hover) {
        g.fill(x, y, x + SCROLL_WIDTH, y + height, SCROLL_TRACK);
        g.fill(x + 1, y + 1, x + SCROLL_WIDTH - 1, y + height - 1, SCROLL_TRACK_INNER);
        g.fill(x, thumbY, x + SCROLL_WIDTH, thumbY + thumbH,
                hover ? SCROLL_THUMB_HOVER : SCROLL_THUMB);
        g.fill(x + 1, thumbY + 1, x + SCROLL_WIDTH - 1, thumbY + thumbH - 1,
                hover ? SCROLL_THUMB_HOVER_LIGHT : SCROLL_THUMB_LIGHT);
    }

    /** thumb 最小高度（文档第 4 节）。 */
    public static final int SCROLL_MIN_THUMB = 20;

    // ==================== 工具函数（文档第 6 节）====================

    /** 缓动：入场用。 */
    public static float easeOutCubic(float t) {
        float f = 1f - t;
        return 1f - f * f * f;
    }

    /** ARGB 线性插值。 */
    public static int blendColors(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }

    private SREPanelStyle() {
    }
}

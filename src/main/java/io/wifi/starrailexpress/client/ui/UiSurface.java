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

package io.wifi.starrailexpress.client.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * <b>扁平 HUD 的形状层</b>（{@code docs/ui_style.md} §3）。
 *
 * <p>
 * 全项目画「面」的地方都从这里取：卡片、面板、全屏底、行态、胶囊开关、滚动条。
 * 主题是 <b>黄铜 HUD</b>：深灰黑底 + 深炭灰面 + 1px 暗铜色描边 + 明黄铜强调，
 * 扁平、无外阴影、无渐变装饰（旧主题那条「上边缘浅色装饰线」已经去掉）。
 *
 * <h2>为什么单独一个类</h2>
 * 以前同一张「卡片」在几十个文件里各画一遍（各自的 {@code fillGradient} + {@code renderOutline}），
 * 圆角、描边粗细、hover 提亮都不同 —— 改一次观感要改几十处。现在只有两组入口：
 * <ul>
 * <li>{@link #card} / {@link #panel} / {@link #backdrop}：面；</li>
 * <li>{@link #switchTrack} / {@link #scrollbar} / {@link #divider} / {@link #rowHighlight}：控件与配件。</li>
 * </ul>
 *
 * <h2>约定（写代码前请先读）</h2>
 * <ol>
 * <li><b>只画形状，不读物主数据</b>：方法收的是颜色与状态，不判断阵营、不查技能。</li>
 * <li><b>不 new 每帧对象</b>：全是静态方法、只吃 {@link GuiGraphics} 与 int。</li>
 * <li><b>坐标先取整</b>：所有 {@code fill} 都落在整数栅格上（半像素会让描边发虚）。</li>
 * <li><b>颜色只能来自 {@link SRETheme}</b>：确需自定义面/描边色时用带颜色的重载，
 * 但传进来的也得是 { SRETheme } 里的色，不要在调用点手写 8 位色值。</li>
 * <li>圆角半径用本类的 {@code RADIUS_*} 常量，不要就地写 3 / 4 这种数字。</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public final class UiSurface {

    /** 卡片 / 面板圆角半径（微圆角）。 */
    public static final int RADIUS_CARD = 4;
    /** 列表行、小卡片的圆角半径。 */
    public static final int RADIUS_ROW = 3;
    /** 徽标、色块的圆角半径。 */
    public static final int RADIUS_CHIP = 2;
    /** 卡片内边距（内容与 1px 描边之间留的呼吸）。 */
    public static final int CARD_PAD = 6;

    private UiSurface() {
    }

    // ══════════════════════════════════════════════════════════════════
    // 1. 形状基元（圆角矩形 / 圆角描边 / 圆）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 圆角实心矩形（逐行内缩近似圆角，整数栅格）。
     *
     * <p>
     * 全项目唯一的圆角填充实现：{@code MinigameUI.roundRect} 已改为转发到这里，
     * 免得小游戏与 HUD 的圆角算法各写一份、圆角大小还不一样。
     */
    public static void roundRect(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int color) {
        int h = y2 - y1;
        if (h <= 0 || x2 <= x1) {
            return;
        }
        if (r <= 0) {
            g.fill(x1, y1, x2, y2, color);
            return;
        }
        r = Math.min(r, Math.min(h / 2, (x2 - x1) / 2));
        for (int row = 0; row < h; row++) {
            int inset = cornerInset(row, h, r);
            g.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, color);
        }
    }

    /** 圆角描边（只画周边一圈，不覆盖内部）。 */
    public static void roundBorder(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int thickness, int color) {
        int h = y2 - y1;
        if (h <= 0 || x2 <= x1 || thickness <= 0) {
            return;
        }
        if (r <= 0) {
            for (int i = 0; i < thickness; i++) {
                g.renderOutline(x1 + i, y1 + i, x2 - x1 - i * 2, y2 - y1 - i * 2, color);
            }
            return;
        }
        r = Math.min(r, Math.min(h / 2, (x2 - x1) / 2));
        for (int row = 0; row < h; row++) {
            int inset = cornerInset(row, h, r);
            int yy = y1 + row;
            if (row < thickness || row >= h - thickness) {
                g.fill(x1 + inset, yy, x2 - inset, yy + 1, color);
            } else {
                g.fill(x1 + inset, yy, x1 + inset + thickness, yy + 1, color);
                g.fill(x2 - inset - thickness, yy, x2 - inset, yy + 1, color);
            }
        }
    }

    /** 实心圆（滑块、圆点徽标）。 */
    public static void circle(GuiGraphics g, int cx, int cy, int radius, int color) {
        roundRect(g, cx - radius, cy - radius, cx + radius + 1, cy + radius + 1, radius, color);
    }

    /** 一行在圆角处需要内缩多少像素。 */
    private static int cornerInset(int row, int height, int r) {
        int edge = Math.min(row, height - 1 - row);
        if (edge >= r) {
            return 0;
        }
        int d = r - edge;
        return r - (int) Math.floor(Math.sqrt((double) r * r - (double) d * d));
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. 面：卡片 / 面板 / 全屏底
    // ══════════════════════════════════════════════════════════════════

    /** 卡片：深炭灰底 + 1px 暗铜色描边 + 微圆角（无外阴影）。 */
    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        card(g, x, y, w, h, SRETheme.CARD_BG, SRETheme.BORDER);
    }

    /** 卡片（自定义底 / 描边）。 */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        card(g, x, y, w, h, fill, border, RADIUS_CARD);
    }

    /**
     * 卡片（自定义底 / 描边 / 圆角）。
     *
     * <p>
     * 传 {@link SRETheme#TRANSPARENT} 当描边就是「无描边」：区内容用的浅底卡片常常不需要边框。
     */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int fill, int border, int radius) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int x2 = x + w;
        int y2 = y + h;
        if (fill != SRETheme.TRANSPARENT) {
            roundRect(g, x, y, x2, y2, radius, fill);
        }
        if (border != SRETheme.TRANSPARENT) {
            roundBorder(g, x, y, x2, y2, radius, 1, border);
        }
    }

    /** 面板：{@link SRETheme#SURFACE_TOP} 底 + 暗铜描边 + 微圆角（整屏/大浮层的底）。 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        card(g, x, y, w, h, SRETheme.SURFACE_TOP, SRETheme.BORDER);
    }

    /** 面板（自定义底 / 描边，用于需要再暗一档的分区）。 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        card(g, x, y, w, h, fill, border, RADIUS_CARD);
    }

    /**
     * 全屏底：极简深灰黑，顶上到底只收暗一点点。
     *
     * <p>
     * 整屏一律走这个方法。旧主题的「斜向纹 + 地平线 + 漂移横线」属于冗余纹理，已按扁平主题去掉。
     */
    public static void backdrop(GuiGraphics g, int width, int height) {
        if (height <= 0) {
            return;
        }
        g.fillGradient(0, 0, width, height, SRETheme.SCREEN_TOP, SRETheme.SCREEN_BOTTOM);
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. 行态：分隔线 / hover / 选中
    // ══════════════════════════════════════════════════════════════════

    /** 1px 行分隔线。 */
    public static void divider(GuiGraphics g, int x, int y, int w) {
        if (w <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + 1, SRETheme.ROW_SEPARATOR);
    }

    /**
     * 列表行底色（无边框）。
     *
     * @param selected 选中 / 当前项：黄铜混色底 + 左侧 2px 黄铜条
     * @param hover    悬停：黄铜 14% 底
     */
    public static void rowHighlight(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hover) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (selected) {
            card(g, x, y, w, h, SRETheme.SELECTED_TOP, SRETheme.BRASS, RADIUS_ROW);
            return;
        }
        if (hover) {
            card(g, x, y, w, h, SRETheme.ROW_HOVER, SRETheme.TRANSPARENT, RADIUS_ROW);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 4. 胶囊开关（开关按钮的唯一画法）
    // ══════════════════════════════════════════════════════════════════

    /** 胶囊开关的推荐高度。 */
    public static final int SWITCH_H = 10;

    /**
     * 胶囊形切换开关：轨道开 = 明黄铜 / 关 = 暗哑青铜灰；滑块 = 银白小圆点。
     *
     * <p>
     * 扁平：轨道纯色无渐变，滑块只有一道 1px 暗边（在黄铜轨道上不糊成一片）。
     *
     * @param on       开 / 关
     * @param knob     滑块位置 0~1（做过渡用；瞬时状态直接传 0 或 1）
     * @param hover    悬停（轨道提亮一档）
     */
    public static void switchTrack(GuiGraphics g, int x, int y, int w, int h, boolean on, float knob, boolean hover) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = h / 2;
        int x2 = x + w;
        int y2 = y + h;
        int track = on
                ? (hover ? SRETheme.SWITCH_TRACK_ON_HOVER : SRETheme.SWITCH_TRACK_ON)
                : (hover ? SRETheme.SWITCH_TRACK_OFF_HOVER : SRETheme.SWITCH_TRACK_OFF);
        roundRect(g, x, y, x2, y2, r, track);
        roundBorder(g, x, y, x2, y2, r, 1, SRETheme.SWITCH_TRACK_EDGE);

        int knobR = Math.max(2, r - 2);
        int travel = Math.max(0, w - 4 - knobR * 2);
        int knobX = x + 2 + Math.round(Mth.clamp(knob, 0.0F, 1.0F) * travel);
        circle(g, knobX + knobR, y + r, knobR, SRETheme.SWITCH_KNOB);
        // 滑块边缘的一道暗边：扁平，但让银白圆点在黄铜轨道上有轮廓
        roundBorder(g, knobX, y + r - knobR, knobX + knobR * 2 + 1, y + r + knobR + 1,
                knobR, 1, SRETheme.SWITCH_KNOB_EDGE);
    }

    // ══════════════════════════════════════════════════════════════════
    // 5. 滚动条
    // ══════════════════════════════════════════════════════════════════

    /** 滚动条宽度（文档 §4：3~7px）。 */
    public static final int SCROLL_WIDTH = 6;

    /**
     * 画滚动条：槽 + thumb（扁平，thumb 是黄铜小条）。
     *
     * @param thumbY thumb 顶端（调用方按自己的滚动几何算好）
     * @param thumbH thumb 高度（调用方保证 ≥ {@code SREPanelStyle.SCROLL_MIN_THUMB}）
     */
    public static void scrollbar(GuiGraphics g, int x, int y, int height, int thumbY, int thumbH, boolean hover) {
        if (height <= 0 || thumbH <= 0) {
            return;
        }
        roundRect(g, x, y, x + SCROLL_WIDTH, y + height, RADIUS_ROW, SRETheme.SCROLL_TRACK);
        roundRect(g, x, thumbY, x + SCROLL_WIDTH, thumbY + thumbH, RADIUS_ROW,
                hover ? SRETheme.SCROLL_THUMB_HOVER : SRETheme.SCROLL_THUMB);
    }
}

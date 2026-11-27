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

import io.wifi.starrailexpress.client.ui.SRETheme;
import io.wifi.starrailexpress.client.ui.UiSurface;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

/**
 * {@code docs/ui_style.md} 指定的界面风格：<b>黄铜 HUD</b>
 * ——深灰黑底（{@code #1A1A1A}）+ 深炭灰卡片（{@code #242424}）+ 1px 暗铜色描边 + 明亮黄铜文字。
 *
 * <p>
 * 面板一律走 {@link UiSurface#card}：<b>纯色填充 → 1px 圆角描边</b>，没有外阴影、没有渐变、
 * 没有上边缘装饰线（旧主题那三步已经按扁平主题收敛成两步）。
 *
 * <h2>色值已全部合并到 {@link SRETheme}</h2>
 * 这里的每个颜色常量都只是 {@link SRETheme} 的转发
 * （{@code static final int = SRETheme.X} 仍是编译期常量，运行时零开销、与直接写数字等价）。
 * 新代码请<b>直接用 {@link SRETheme} 与 {@link UiSurface}</b>；本类保留只是为了不打断已有的
 * 「{@code SREPanelStyle.PANEL_BG_TOP}」这类调用面，以及它手里那套面板绘制方法。
 */
@Environment(EnvType.CLIENT)
public final class SREPanelStyle {

    // ==================== 基础色板（转发到 SRETheme）====================

    /** 面板背景（上）。 */
    public static final int PANEL_BG_TOP = SRETheme.SURFACE_TOP;
    /** 面板背景（下）。 */
    public static final int PANEL_BG_BOTTOM = SRETheme.SURFACE_BOTTOM;
    /** 面板背景（下，偏蓝棕黑，做上下渐变用）。 */
    public static final int PANEL_BG_BOTTOM_ALT = SRETheme.SURFACE_BOTTOM_ALT;
    /** 全屏背景（上）。 */
    public static final int SCREEN_BG_TOP = SRETheme.SCREEN_TOP;
    /** 全屏背景（下）。 */
    public static final int SCREEN_BG_BOTTOM = SRETheme.SCREEN_BOTTOM;
    /** 面板边框（棕褐）。 */
    public static final int BORDER = SRETheme.BORDER;
    /** 顶部装饰线（半透明浅米）。 */
    public static final int TOP_LINE = SRETheme.DECOR_LINE;
    /** 亮金色（标题 / hover 边框 / 滚动条）。 */
    public static final int GOLD = SRETheme.GOLD;
    /** 棕金色（次强调）。 */
    public static final int GOLD_DIM = SRETheme.GOLD_DIM;
    /** 主文字（浅奶油）。 */
    public static final int TEXT = SRETheme.TEXT;
    /** 标题文字（浅米）。 */
    public static final int TITLE = SRETheme.TITLE;
    /** 次要文字（土褐）。 */
    public static final int MUTED = SRETheme.MUTED;
    /** 输入框占位提示色（见 {@link SRETheme#HINT} 的说明）。 */
    public static final int HINT = SRETheme.HINT;
    /** 暗米色正文。 */
    public static final int BODY = SRETheme.BODY;
    /** 功能蓝（计时 / 地图属性）。 */
    public static final int BLUE = SRETheme.BLUE;
    /** 功能绿（确认 / 场景方块）。 */
    public static final int GREEN = SRETheme.GREEN;
    /** 功能红（警告 / 错误）。 */
    public static final int RED = SRETheme.RED;
    /** 功能琥珀（任务方块 / 代币）。 */
    public static final int AMBER = SRETheme.AMBER;

    // ==================== 交互状态色（文档 2.3）====================

    /** hover 高亮背景。 */
    public static final int ROW_HOVER = SRETheme.ROW_HOVER;
    /** 行分隔线。 */
    public static final int ROW_SEPARATOR = SRETheme.ROW_SEPARATOR;
    /** 非活跃卡片边框。 */
    public static final int CARD_BORDER = SRETheme.CARD_BORDER;
    /** 选中 / 活跃行背景（上）。 */
    public static final int SELECTED_TOP = SRETheme.SELECTED_TOP;
    /** 选中 / 活跃行背景（下）。 */
    public static final int SELECTED_BOTTOM = SRETheme.SELECTED_BOTTOM;

    // ==================== 滚动条 ====================

    /** 滚动条宽（文档第 4 节：3~7px；唯一实现在 {@link UiSurface#SCROLL_WIDTH}）。 */
    public static final int SCROLL_WIDTH = UiSurface.SCROLL_WIDTH;
    /** 滚动条槽底色。 */
    public static final int SCROLL_TRACK = SRETheme.SCROLL_TRACK;
    /** 滚动条槽内侧。 */
    public static final int SCROLL_TRACK_INNER = SRETheme.SCROLL_TRACK_INNER;
    /** 滚动条 thumb。 */
    public static final int SCROLL_THUMB = SRETheme.SCROLL_THUMB;
    /** 滚动条 thumb 亮部。 */
    public static final int SCROLL_THUMB_LIGHT = SRETheme.SCROLL_THUMB_LIGHT;
    /** 滚动条 thumb hover。 */
    public static final int SCROLL_THUMB_HOVER = SRETheme.SCROLL_THUMB_HOVER;
    /** 滚动条 thumb hover 亮部。 */
    public static final int SCROLL_THUMB_HOVER_LIGHT = SRETheme.SCROLL_THUMB_HOVER_LIGHT;
    /** thumb 最小高度（文档第 4 节）。 */
    public static final int SCROLL_MIN_THUMB = 20;

    // ==================== 面板绘制（文档第 3 节）====================

    /** 画一个面板：纯色底 + 1px 圆角暗铜描边（扁平，无渐变/无装饰线）。 */
    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        UiSurface.panel(g, x, y, w, h);
    }

    /**
     * 画一个面板（自定义底色）。
     *
     * <p>
     * 旧签名收「上/下两个颜色」是为了做渐变；扁平主题里底色不再做渐变，
     * 所以<b>只取 {@code top} 当纯色底</b>，{@code bottom} 仅为兼容旧调用保留
     * （两边在旧主题里本来就是同族的深浅两档，换成纯色后观感一致）。
     */
    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        UiSurface.card(g, x, y, w, h, top, BORDER);
    }

    /** 只画一条页脚分隔线（用于已经画好背景的确认/取消这类页脚条）。 */
    public static void drawFooterLine(GuiGraphics g, int x, int y, int w) {
        UiSurface.divider(g, x, y, w);
    }

    // ==================== 滚动条绘制 ====================

    /**
     * 画滚动条（槽 + thumb）。
     *
     * <p>
     * 实现在 {@link UiSurface#scrollbar}：扁平槽 + 黄铜 thumb（hover 提亮）。
     *
     * @param thumbY thumb 顶端（由调用方按自己的滚动几何算出）
     * @param thumbH thumb 高度（调用方保证 ≥ {@link #SCROLL_MIN_THUMB}）
     */
    public static void drawScrollbar(GuiGraphics g, int x, int y, int height, int thumbY, int thumbH, boolean hover) {
        UiSurface.scrollbar(g, x, y, height, thumbY, thumbH, hover);
    }

    // ==================== 工具函数（文档第 6 节）====================

    /**
     * 给输入框的占位提示套上 {@link #HINT} 色。
     *
     * <p>
     * 原版 {@code EditBox} 画 hint 时用的是输入框自己的文字色，所以必须给 hint 文案带上颜色样式
     * （样式优先于传入的颜色），否则它和真实内容同色，用户会以为框里已经有内容了。
     *
     * @param text 占位提示文案，null 原样返回
     */
    public static net.minecraft.network.chat.Component hint(net.minecraft.network.chat.Component text) {
        if (text == null) {
            return null;
        }
        return text.copy().withStyle(style -> style.withColor(HINT));
    }

    /** 缓动：入场用。 */
    public static float easeOutCubic(float t) {
        float f = 1f - t;
        return 1f - f * f * f;
    }

    /** ARGB 线性插值（唯一实现在 {@link SRETheme#blendColors}）。 */
    public static int blendColors(int c1, int c2, float t) {
        return SRETheme.blendColors(c1, c2, t);
    }

    private SREPanelStyle() {
    }
}

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

/**
 * <b>全项目唯一的界面色板真源</b>（{@code docs/ui_style.md} §2 / {@code docs/ui_unified_style_plan.md} §3）。
 *
 * <p>
 * 复古列车 / 老式车票质感：深棕黑打底 + 米色文字 + 金色点缀。
 *
 * <h2>为什么要有这个类</h2>
 * 以前同一套色值在三个地方各声明了一遍 —— {@code SREPanelStyle}（游戏内面板）、
 * {@code SreUiStyle}（主菜单 / 加载 / 连接中）、{@code VoteFlowFrame}（投票旅程）。
 * 三份数值本来一致，于是「改一次主色要改三处」；但只要有任何一处被单独调整，
 * 界面就会悄悄分裂成两种观感。现在三处都改成转发到这里：
 * {@code public static final int X = SRETheme.X;}，
 * 因为目标仍是编译期常量（{@code static final int} + 常量初始化），
 * <b>消息里内联、运行时零开销</b>，与直接写数字完全等价。
 *
 * <h2>加色值的规矩</h2>
 * <ol>
 * <li>新界面优先复用现有名字；确实需要新色，加到本类并在此处写清用途。</li>
 * <li><b>语义色只表达状态</b>（就绪 / 冷却 / 危险 / 不可用），<b>不要拿它做装饰</b>；
 * 装饰一律 {@link #GOLD} / {@link #DECOR_LINE} / {@link #CARD_BORDER}。</li>
 * <li>阵营色不在这里 —— 走 {@link io.wifi.starrailexpress.api.RoleTeam}（唯一权威阵营来源）。</li>
 * <li>布局尺寸（栅格、行高、滚动条宽度）不在这里，属于面板绘制 / 布局层。</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public final class SRETheme {

    private SRETheme() {
    }

    // ==================== 表面 / 底板 ====================

    /** 面板背景（上）：深棕红，半透明。 */
    public static final int SURFACE_TOP = 0xD81A1008;
    /**
     * 面板背景（上，更薄的一档）。
     *
     * <p>
     * 只给「面板里还叠着一层内容」的位置用（投票旅程的卡片底就是它），
     * 比 {@link #SURFACE_TOP} 透一点，透出后面的全屏背景。
     */
    public static final int SURFACE_TOP_SOFT = 0xB81A1008;
    /** 面板背景（下）：棕黑。 */
    public static final int SURFACE_BOTTOM = 0xD820140A;
    /** 面板背景（下）：偏蓝棕黑，做上下渐变时与 {@link #SURFACE_BOTTOM} 二选一。 */
    public static final int SURFACE_BOTTOM_ALT = 0xD80B1722;
    /** 全屏背景（上）：更实的深棕（全屏级界面用）。 */
    public static final int SCREEN_TOP = 0xF018120A;
    /** 全屏背景（下）：更实的暗蓝黑。 */
    public static final int SCREEN_BOTTOM = 0xF0061018;

    // ==================== 基础色 ====================

    /** 纯黑：遮罩、压暗、黑场。 */
    public static final int BLACK = 0xFF000000;
    /** 全透明：淡入淡出的起点（配合 {@link #withAlpha} 或 {@code LoadingFx.lerpArgb}）。 */
    public static final int TRANSPARENT = 0x00000000;
    /** 只取 RGB 的掩码：{@code alpha << 24 | (color & RGB)}。 */
    public static final int RGB = 0x00FFFFFF;

    // ==================== 描边 / 装饰 ====================

    /** 面板边框：棕褐色。 */
    public static final int BORDER = 0xFF8B6914;
    /** 顶部装饰线（面板上边缘内侧 1px，半透明浅米）。 */
    public static final int DECOR_LINE = 0x33FFE8C0;
    /** 非活跃卡片边框（比 {@link #BORDER} 暗一档）。 */
    public static final int CARD_BORDER = 0xFF5A4530;
    /** 行分隔线。 */
    public static final int ROW_SEPARATOR = 0x20FFFFFF;
    /** hover 高亮背景。 */
    public static final int ROW_HOVER = 0x22FFFFFF;

    // ==================== 强调 / 文字 ====================

    /** 亮金：标题、hover 边框、滚动条 thumb、数值。 */
    public static final int GOLD = 0xFFD4AF37;
    /** 棕金：次强调，也是选中行的混色基准。 */
    public static final int GOLD_DIM = 0xFFC9A84C;
    /** 主文字：浅奶油。 */
    public static final int TEXT = 0xFFFFF4DC;
    /** 标题文字：浅米。 */
    public static final int TITLE = 0xFFF5E8C8;
    /** 次要文字：土褐（说明、版本号、占位符）。 */
    public static final int MUTED = 0xFF9E8B6E;
    /**
     * 输入框占位提示色。
     *
     * <p>
     * 取的就是 {@link #MUTED}：原版 {@code EditBox} 画 hint 用的是<b>输入框自己的文字色</b>，
     * 不额外套样式的话提示会和真实输入内容一模一样，用户会以为框里已经有内容了。
     */
    public static final int HINT = MUTED;
    /** 暗米色长段正文。 */
    public static final int BODY = 0xFFC8B898;

    // ==================== 选中态 ====================

    /** 选中 / 活跃行背景（上）。 */
    public static final int SELECTED_TOP = blendColors(0xFF1A1008, GOLD_DIM, 0.32F);
    /** 选中 / 活跃行背景（下）。 */
    public static final int SELECTED_BOTTOM = blendColors(0xFF120A04, GOLD_DIM, 0.18F);

    // ==================== 语义色（只表达状态） ====================

    /** 功能蓝：计时、地图属性、冷却中。 */
    public static final int BLUE = 0xFF5EB7D8;
    /** 功能绿：确认、就绪、场景方块。 */
    public static final int GREEN = 0xFF72C17B;
    /** 功能红：危险、警告、错误。 */
    public static final int RED = 0xFFE06B65;
    /** 功能琥珀：任务方块、代币、小游戏进度。 */
    public static final int AMBER = 0xFFE0AD5B;

    // ==================== 滚动条 ====================

    /** 滚动条槽底色。 */
    public static final int SCROLL_TRACK = 0x66120A04;
    /** 滚动条槽内侧（与 {@link #DECOR_LINE} 同值，但语义是「槽」而不是「装饰线」）。 */
    public static final int SCROLL_TRACK_INNER = 0x33FFE8C0;
    /** 滚动条 thumb。 */
    public static final int SCROLL_THUMB = 0xFFB8912F;
    /** 滚动条 thumb 亮部。 */
    public static final int SCROLL_THUMB_LIGHT = 0xFFD4AF37;
    /** 滚动条 thumb hover。 */
    public static final int SCROLL_THUMB_HOVER = 0xFFD4AF37;
    /** 滚动条 thumb hover 亮部。 */
    public static final int SCROLL_THUMB_HOVER_LIGHT = 0xFFFFE8C0;

    // ==================== 工具 ====================

    /**
     * ARGB 线性插值。
     *
     * <p>
     * {@code docs/ui_style.md} §6 的规范实现，全项目只留这一份；
     * {@code SREPanelStyle.blendColors} 已改为转发到这里。
     */
    public static int blendColors(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }

    /** 给 ARGB 里的 RGB 换一个 alpha（0~255），用于淡入淡出。 */
    public static int withAlpha(int argb, int alpha) {
        return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }
}

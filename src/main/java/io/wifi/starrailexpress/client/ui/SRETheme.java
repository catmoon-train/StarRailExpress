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
 * 当前主题：<b>黄铜 HUD</b>——深灰黑极简底（{@code #1A1A1A}）+ 深炭灰卡片（{@code #242424}）
 * + 1px 暗铜色描边（{@code #4A3B2A}）+ 明亮黄铜文字（{@code #D9A441}）。
 * 扁平、无外阴影、无冗余纹理，只靠「黄铜 × 深灰」的强反差建立复古又现代的仪表感。
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
 * 装饰一律 {@link #BRASS} / {@link #BORDER} / {@link #CARD_BG}。</li>
 * <li>阵营色不在这里 —— 走 {@link io.wifi.starrailexpress.api.RoleTeam}（唯一权威阵营来源）。</li>
 * <li>布局尺寸（栅格、行高、滚动条宽度、圆角半径）不在这里，属于面板绘制 / 布局层
 * （圆角半径见 {@code io.wifi.starrailexpress.client.ui.UiSurface}）。</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public final class SRETheme {

    private SRETheme() {
    }

    // ==================== 表面 / 底板 ====================

    /** 全屏背景 / 面板底：极简深灰黑 {@code #1A1A1A}。 */
    public static final int SURFACE_TOP = 0xFF1A1A1A;
    /**
     * 面板背景（更薄的一档）。
     *
     * <p>
     * 只给「面板里还叠着一层内容」的位置用（投票旅程的卡片底就是它），
     * 比 {@link #SURFACE_TOP} 透一点，透出后面的全屏背景。
     */
    public static final int SURFACE_TOP_SOFT = 0xE61A1A1A;
    /**
     * 卡片底色：深炭灰 {@code #242424}。
     *
     * <p>
     * 旧主题这里是「渐变的下半段」，新主题是<b>纯色</b>：卡片不再做上下渐变，
     * 只靠 1px 暗铜色描边和与背景的明度差把它从 {@link #SURFACE_TOP} 上「提」起来。
     */
    public static final int SURFACE_BOTTOM = 0xFF242424;
    /** 分区底：比卡片再暗一档的炭灰（面板里再分区时用）。 */
    public static final int SURFACE_BOTTOM_ALT = 0xFF1F1F1F;
    /** 卡片底（与 {@link #SURFACE_BOTTOM} 同值的语义名，新代码用这个）。 */
    public static final int CARD_BG = SURFACE_BOTTOM;
    /** 全屏背景（上）：深灰黑。 */
    public static final int SCREEN_TOP = 0xFF1A1A1A;
    /** 全屏背景（下）：极轻的收暗（几乎看不出渐变，只为避免整屏死平）。 */
    public static final int SCREEN_BOTTOM = 0xFF141414;

    // ==================== 基础色 ====================

    /** 纯黑：遮罩、压暗、黑场。 */
    public static final int BLACK = 0xFF000000;
    /** 全透明：淡入淡出的起点（配合 {@link #withAlpha} 或 {@code LoadingFx.lerpArgb}）。 */
    public static final int TRANSPARENT = 0x00000000;
    /** 只取 RGB 的掩码：{@code alpha << 24 | (color & RGB)}。 */
    public static final int RGB = 0x00FFFFFF;

    // ==================== 描边 / 装饰 ====================

    /** 面板 / 卡片描边：暗铜色 1px。 */
    public static final int BORDER = 0xFF4A3B2A;
    /** 暗铜色（与 {@link #BORDER} 同值的语义名）。 */
    public static final int COPPER = BORDER;
    /**
     * 顶部装饰线。
     *
     * <p>
     * 新主题是<b>扁平</b>的，不再往面板上边缘画那条浅色装饰线，所以这里是全透明；
     * 字段保留是为了不打断旧调用（画上去等于什么都没画）。要一条分隔线请用
     * {@link io.wifi.starrailexpress.client.ui.UiSurface#divider} 或 {@link #ROW_SEPARATOR}。
     */
    public static final int DECOR_LINE = 0x00000000;
    /** 非活跃卡片 / 次级描边（比 {@link #BORDER} 再暗一档的铜）。 */
    public static final int CARD_BORDER = 0xFF352A1E;
    /** 更暗的铜色描边（与 {@link #CARD_BORDER} 同值的语义名）。 */
    public static final int COPPER_DIM = CARD_BORDER;
    /** 行分隔线：黄铜 10%。 */
    public static final int ROW_SEPARATOR = 0x1AD9A441;
    /** hover 高亮背景：黄铜 14%（不再是白色叠层，保持暖色统一）。 */
    public static final int ROW_HOVER = 0x24D9A441;

    // ==================== 强调 / 文字（黄铜）====================

    /** 亮黄铜：标题、hover 边框、滚动条 thumb、数值。 */
    public static final int GOLD = 0xFFD9A441;
    /** 黄铜（与 {@link #GOLD} 同值的语义名，新代码用这个）。 */
    public static final int BRASS = GOLD;
    /** 高亮黄铜：hover / 选中 / 聚焦态的描边与文字。 */
    public static final int BRASS_BRIGHT = 0xFFF0C878;
    /** 做旧暗黄铜：次强调，也是选中行的混色基准。 */
    public static final int GOLD_DIM = 0xFF8A7346;
    /** 暗哑青铜灰：胶囊开关的「关」态轨道。 */
    public static final int BRONZE = 0xFF4A4438;
    /** 主文字：明亮黄铜。 */
    public static final int TEXT = 0xFFD9A441;
    /** 标题文字：高亮黄铜。 */
    public static final int TITLE = 0xFFF0C878;
    /** 次要文字：做旧暗铜（说明、版本号、占位符）。 */
    public static final int MUTED = 0xFF8A7346;
    /**
     * 输入框占位提示色。
     *
     * <p>
     * 取的就是 {@link #MUTED}：原版 {@code EditBox} 画 hint 用的是<b>输入框自己的文字色</b>，
     * 不额外套样式的话提示会和真实输入内容一模一样，用户会以为框里已经有内容了。
     */
    public static final int HINT = MUTED;
    /** 正文：比 {@link #TEXT} 暗一档的暖金，长段落用它更耐看。 */
    public static final int BODY = 0xFFC09A5A;
    /**
     * 文字硬阴影色。
     *
     * <p>
     * 黄铜文字压在深灰底上对比很强，但压在浅色物品图标 / 场景上会糊；原版字体渲染器的
     * 阴影是「把字色压暗一档画在右下 1px」，这里给的是<b>固定纯黑</b>的版本：
     * 需要自己画阴影（描边字、图标上的数字）时取这个色，配合 {@code UiText} 使用。
     */
    public static final int INK = 0xFF0A0A0A;
    /** 文字硬阴影（与 {@link #INK} 同值的语义名）。 */
    public static final int TEXT_SHADOW = INK;

    // ==================== 选中态 ====================

    /** 选中 / 活跃行背景（上）：卡片色混一点黄铜。 */
    public static final int SELECTED_TOP = blendColors(0xFF242424, GOLD_DIM, 0.30F);
    /** 选中 / 活跃行背景（下）：扁平主题下与 {@link #SELECTED_TOP} 同值（不再做渐变）。 */
    public static final int SELECTED_BOTTOM = SELECTED_TOP;

    // ==================== 胶囊开关（开关按钮的唯一写法）====================

    /** 开关轨道（开）：明亮黄铜。 */
    public static final int SWITCH_TRACK_ON = GOLD;
    /** 开关轨道（开，hover）：高亮黄铜。 */
    public static final int SWITCH_TRACK_ON_HOVER = BRASS_BRIGHT;
    /** 开关轨道（关）：暗哑青铜灰。 */
    public static final int SWITCH_TRACK_OFF = BRONZE;
    /** 开关轨道（关，hover）：比关态稍亮一点，提示「点得动」。 */
    public static final int SWITCH_TRACK_OFF_HOVER = 0xFF5A5346;
    /** 开关轨道 1px 描边（压住轨道边缘，扁平但不糊边）。 */
    public static final int SWITCH_TRACK_EDGE = 0xFF2A2620;
    /** 开关滑块：金属亮银白（扁平，只有边缘一道高光）。 */
    public static final int SWITCH_KNOB = 0xFFEDEDED;
    /** 开关滑块边缘：一道暗边，让银白滑块在黄铜轨道上不糊成一片。 */
    public static final int SWITCH_KNOB_EDGE = 0xFF141414;

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
    public static final int SCROLL_TRACK = 0x66101010;
    /** 滚动条槽内侧（扁平主题下不再画内槽，保持透明）。 */
    public static final int SCROLL_TRACK_INNER = 0x00000000;
    /** 滚动条 thumb：做旧暗黄铜。 */
    public static final int SCROLL_THUMB = 0xFF6E5A32;
    /** 滚动条 thumb 亮部：黄铜。 */
    public static final int SCROLL_THUMB_LIGHT = 0xFFD9A441;
    /** 滚动条 thumb hover：黄铜。 */
    public static final int SCROLL_THUMB_HOVER = 0xFFD9A441;
    /** 滚动条 thumb hover 亮部：高亮黄铜。 */
    public static final int SCROLL_THUMB_HOVER_LIGHT = 0xFFF0C878;

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

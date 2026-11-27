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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>扁平 HUD 的文字层</b>：带缓存的翻译键工厂 + 统一「黄铜字 + 黑色硬阴影」的绘制入口
 * （{@code docs/ui_style.md} §5）。
 *
 * <h2>为什么要有这个类</h2>
 * <ol>
 * <li><b>每帧新建 Component 是真的在花钱</b>：{@code Component.translatable(...)} + {@code getString()}
 * 每次都会走一遍翻译查找与参数格式化；HUD 每帧、每个元素各来一次就是稳定的 GC 抖动。
 * 这里按「键 + 参数」缓存，翻译字典在运行期不变，所以缓存不需要失效。</li>
 * <li><b>阴影要统一</b>：黄铜文字压在深灰底、浅色物品图标或场景上时，没阴影会糊。
 * 主题要求「细微的黑色硬阴影」，原版 {@code drawString(..., true)} 就是右下 1px 的硬阴影；
 * 这里默认开启，调用点不用再逐个记。</li>
 * <li><b>居中/右对齐的 API 不统一</b>：{@code drawCenteredString} 不带阴影，
 * 想居中又要阴影就只能自己算 x；这里把三种对齐都包好。</li>
 * </ol>
 *
 * <p>
 * 约定：<b>渲染路径里不要直接 {@code Component.translatable} </b>，走 {@link #of}；
 * 文字颜色只能取 {@link SRETheme} 的语义位（标题 {@link SRETheme#TITLE}、
 * 正文 {@link SRETheme#TEXT}、次要 {@link SRETheme#MUTED}、正文长段落 {@link SRETheme#BODY}）。
 * 确实不合适用阴影时（例如要画的其实是图标占位）用 {@link #drawPlain}。
 */
@Environment(EnvType.CLIENT)
public final class UiText {

    /** 缓存上限：超了就整体清空（正常界面几百条足够，避免长跑攒出无界内存）。 */
    private static final int CACHE_LIMIT = 512;

    /** 「翻译键 + 参数」→ Component。 */
    private static final Map<String, Component> CACHE = new ConcurrentHashMap<>();

    private UiText() {
    }

    // ══════════════════════════════════════════════════════════════════
    // 1. 文案工厂（带缓存）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 翻译键 → {@link Component}（缓存）。
     *
     * <p>
     * 参数请用字符串 / 数字这类稳定值；传 {@link Component} 时它自己的 {@code toString()}
     * 会参与缓存键（同键同文案命中、换了文案就重新构造，只是多占几个条目）。
     */
    public static Component of(String key, Object... args) {
        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear();
        }
        String cacheKey = args.length == 0 ? "T:" + key : "T:" + key + '|' + Arrays.deepToString(args);
        return CACHE.computeIfAbsent(cacheKey, ignored -> Component.translatable(key, args));
    }

    /** 纯文本 → {@link Component}（缓存）。用在「不能走翻译键」的拼接结果上。 */
    public static Component literal(String text) {
        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear();
        }
        return CACHE.computeIfAbsent("L:" + text, ignored -> Component.literal(text));
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. 绘制（默认带硬阴影）
    // ══════════════════════════════════════════════════════════════════

    /** 左对齐绘制（带硬阴影），返回文字宽度。 */
    public static int draw(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        if (text == null || text.getString().isEmpty()) {
            return 0;
        }
        g.drawString(font, text, x, y, color, true);
        return font.width(text);
    }

    /** 居中绘制（带硬阴影），返回文字宽度。 */
    public static int drawCentered(GuiGraphics g, Font font, Component text, int centerX, int y, int color) {
        if (text == null || text.getString().isEmpty()) {
            return 0;
        }
        int width = font.width(text);
        g.drawString(font, text, centerX - width / 2, y, color, true);
        return width;
    }

    /** 右对齐绘制（带硬阴影），返回文字宽度。 */
    public static int drawRight(GuiGraphics g, Font font, Component text, int rightX, int y, int color) {
        if (text == null || text.getString().isEmpty()) {
            return 0;
        }
        int width = font.width(text);
        g.drawString(font, text, rightX - width, y, color, true);
        return width;
    }

    /** 不带阴影的左对齐绘制（要自己画描边、或压在图上的文字用）。 */
    public static int drawPlain(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        if (text == null || text.getString().isEmpty()) {
            return 0;
        }
        g.drawString(font, text, x, y, color, false);
        return font.width(text);
    }

    /**
     * 给文字画一圈纯黑描边（{@link SRETheme#INK}），再画正文。
     *
     * <p>
     * 用在「压在亮色贴图/场景上、硬阴影也压不住」的少数位置（HUD 数字、结算标题）。
     * 描边是 4 向 1px，不是模糊阴影 —— 与主题要求的「扁平、硬边」一致。
     */
    public static int drawOutlined(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        if (text == null || text.getString().isEmpty()) {
            return 0;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    g.drawString(font, text, x + dx, y + dy, SRETheme.INK, false);
                }
            }
        }
        g.drawString(font, text, x, y, color, false);
        return font.width(text);
    }

    /** 按最大宽度换行后逐行绘制（带硬阴影），返回下一行的 y。 */
    public static int drawWrapped(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int lineHeight,
            int color) {
        if (text == null || text.getString().isEmpty()) {
            return y;
        }
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            g.drawString(font, line, x, y, color, true);
            y += lineHeight;
        }
        return y;
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. 层级（字号只有三档，不要再就地 scale）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 一套「标题 / 正文 / 注释」的颜色。
     *
     * <p>
     * 只有三档：标题用 {@link SRETheme#TITLE}、正文 {@link SRETheme#TEXT}、
     * 注释与次要信息 {@link SRETheme#MUTED}。要更细的层级请用粗体而不是新颜色。
     */
    public static final class Level {
        public static final int HERO = SRETheme.TITLE;
        public static final int TITLE = SRETheme.TITLE;
        public static final int SECTION = SRETheme.BRASS;
        public static final int BODY = SRETheme.TEXT;
        public static final int NOTE = SRETheme.MUTED;

        private Level() {
        }
    }

    /** 把样式里的颜色换成主题色（{@code Component} 自带颜色的情况）。 */
    public static MutableComponent tinted(Component text, int color) {
        return text.copy().withStyle(style -> style.withColor(color));
    }
}

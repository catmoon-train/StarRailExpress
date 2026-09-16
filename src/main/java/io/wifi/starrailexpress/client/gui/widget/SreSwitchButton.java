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

package io.wifi.starrailexpress.client.gui.widget;

import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.MinigameUI;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * SRE 的开关按钮 —— 四个自定义工具编辑器与地图工具共用这一份实现（外观完全一致）。
 *
 * <p>
 * 状态不写成文字，而是画成一个<b>带颜色的方块</b>：开 = 绿 ✓、关 = 红 ✗、未设置 = 土褐 -
 * （未设置用 ASCII 短横，任何字体下都不会变成方块）。悬停时方块向自己的强调色靠一点，
 * 点下去之前就能确认点的是哪一项。
 *
 * <p>
 * 标题有两种摆法：{@code label} 非空时「左标题 + 右方块」（按钮自带标题的行用，相邻的这种行会自动并排）；
 * 标题为空时把「方块 + 状态文字」居中（标题已经画在标签列上的行用）。
 */
@Environment(EnvType.CLIENT)
public class SreSwitchButton extends SreButton {

    /** 状态符号方块边长。 */
    private static final int CHIP = 12;
    /** 状态方块底色（混一点深色底，免得高饱和色整块太亮）。 */
    private static final int CHIP_ON_BG = SREPanelStyle.blendColors(0xFF120A04, SREPanelStyle.GREEN, 0.42F);
    private static final int CHIP_OFF_BG = SREPanelStyle.blendColors(0xFF120A04, SREPanelStyle.RED, 0.42F);
    private static final int CHIP_UNSET_BG = SREPanelStyle.blendColors(0xFF120A04, SREPanelStyle.MUTED, 0.32F);
    private static final String SYMBOL_ON = "✓";
    private static final String SYMBOL_OFF = "✗";
    /** 未设置：用 ASCII 的短横，任何字体下都不会变成方块。 */
    private static final String SYMBOL_UNSET = "-";

    private final Supplier<SwitchState> state;
    private final Function<SwitchState, Component> stateText;

    /**
     * @param label     按钮自带的标题；为 null 表示标题画在标签列上（按钮里只放「方块 + 状态文字」）
     * @param state     当前状态（{@link SwitchState#UNSET} = 未设置，画成土褐色的短横）
     * @param stateText 状态文字（可为 null，那就只画方块）；收 {@link SwitchState}，不用防 null
     */
    public SreSwitchButton(Font font, Component label, Supplier<SwitchState> state,
            Function<SwitchState, Component> stateText, OnPress onPress) {
        super(font, label == null ? Component.empty() : label, onPress);
        this.state = state;
        this.stateText = stateText;
    }

    /**
     * 刚好放得下这个按钮的宽度（标题按文字实测，带状态词的算上状态词）。
     *
     * <p>
     * 相邻开关行并排时会用<b>一行里最宽的那个</b>把这一行取齐（见
     * {@code CustomEditorScreen#mergeSwitchRows}）：短标题的按钮不会拉成一大条空按钮，一行内两个按钮也等宽。
     */
    public static int naturalWidth(Font font, AbstractWidget widget) {
        if (font == null || widget == null) {
            return 0;
        }
        if (widget instanceof SreSwitchButton button) {
            return button.naturalWidth(font);
        }
        String text = widget.getMessage().getString();
        return text.isEmpty() ? 0 : font.width(text) + CHIP + 14;
    }

    /**
     * 一个就地的「是 / 否」开关（标题为空、状态画成方块 + 状态文字）：点一下切换，把新值交给 setter。
     *
     * <p>
     * 地图工具那种「一行一个设置、标签在标签列上」的地方用这个，之后自己摆位置（{@link #at}）。
     */
    public static SreSwitchButton toggle(Font font, boolean initial, Function<SwitchState, Component> stateText,
            Consumer<Boolean> setter) {
        return toggle(font, null, initial, stateText, setter);
    }

    /** 同上，但按钮自带标题（标题 + 状态词 + 方块都在按钮里）。 */
    public static SreSwitchButton toggle(Font font, Component title, boolean initial,
            Function<SwitchState, Component> stateText, Consumer<Boolean> setter) {
        SwitchState[] value = { SwitchState.of(initial) };
        return new SreSwitchButton(font, title, () -> value[0], stateText, b -> {
            // 两态开关只走「开 ⇄ 关」，永远到不了「未设置」
            value[0] = value[0].toggled();
            setter.accept(value[0].isOn());
        });
    }

    /** 就地摆位置（链式，地图工具用）。 */
    public SreSwitchButton at(int x, int y, int width, int height) {
        setX(x);
        setY(y);
        setWidth(width);
        setHeight(height);
        refreshTooltip();
        return this;
    }

    /** 宽度：左边距 5 + 标题 + （状态词 + 6）+ 方块 + 右边距（+1 余量），与 {@link #renderString} 对齐。 */
    private int naturalWidth(Font font) {
        int width = font.width(getMessage()) + stateWordWidth(font, state.get()) + 6;
        // 三种状态里挑最宽的那个：切换状态时按钮宽度不会跳
        for (SwitchState candidate : SwitchState.values()) {
            width = Math.max(width, font.width(getMessage()) + stateWordWidth(font, candidate) + 6);
        }
        return width + CHIP + 14;
    }

    /**
     * 状态词的宽度。
     *
     * <p>
     * 三种状态都能安全地量：状态词函数收的是 {@link SwitchState}（不是可空 Boolean），
     * 写 {@code switch} 时编译器还会强制覆盖三种状态，所以不存在「某个取值没处理」的崩溃。
     */
    private int stateWordWidth(Font font, SwitchState value) {
        if (stateText == null) {
            return 0;
        }
        Component word = stateText.apply(value);
        return word == null ? 0 : font.width(word);
    }

    @Override
    protected int textLimit() {
        // 右侧要给状态符号留位置
        return Math.max(8, getWidth() - CHIP - 12);
    }

    @Override
    protected Component fullText() {
        // 标题在标签列上时这里只放状态文字，没有会被裁掉的内容
        return hasLabel() ? getMessage() : Component.empty();
    }

    @Override
    public void renderString(GuiGraphics g, Font font, int color) {
        SwitchState value = state.get();
        int bg = chipColor(value);
        int border = chipBorder(value);
        String symbol = chipSymbol(value);
        if (isHovered()) {
            // 悬停时状态方块向自己的强调色靠一点，点下去之前就能确认点的是哪一项
            bg = SREPanelStyle.blendColors(bg, border, 0.35F);
        }
        int chipY = getY() + (getHeight() - CHIP) / 2;
        Component word = stateText == null ? null : stateText.apply(value);
        int wordWidth = word == null ? 0 : font.width(word) + 6;
        int textY = getY() + (getHeight() - 8) / 2;

        if (hasLabel()) {
            int chipX = getX() + getWidth() - CHIP - 4;
            drawChip(g, font, chipX, chipY, bg, border, symbol);
            // 状态词贴在方块左边（右对齐），标题被截断也不会盖住它
            if (word != null) {
                g.drawString(font, word, chipX - wordWidth, textY, border, false);
            }
            String shown = MapUiGraphics.clip(font, getMessage().getString(),
                    Math.max(8, textLimit() - wordWidth));
            // 关 / 未设置时整行暗一档，开的时候才是亮的
            int labelColor = value.isOn() ? SREPanelStyle.TEXT : SREPanelStyle.MUTED;
            g.drawString(font, Component.literal(shown), getX() + 5, textY,
                    isHovered() ? SREPanelStyle.TEXT : labelColor, false);
            return;
        }

        // 标题在标签列上的行：把「方块 + 状态文字」居中
        int groupWidth = CHIP + wordWidth;
        int chipX = getX() + (getWidth() - groupWidth) / 2;
        drawChip(g, font, chipX, chipY, bg, border, symbol);
        if (word != null) {
            g.drawString(font, word, chipX + CHIP + 4, textY, border, false);
        }
    }

    private boolean hasLabel() {
        return !getMessage().getString().isEmpty();
    }

    private static int chipColor(SwitchState value) {
        return switch (value) {
            case ON -> CHIP_ON_BG;
            case OFF -> CHIP_OFF_BG;
            case UNSET -> CHIP_UNSET_BG;
        };
    }

    private static int chipBorder(SwitchState value) {
        return switch (value) {
            case ON -> SREPanelStyle.GREEN;
            case OFF -> SREPanelStyle.RED;
            case UNSET -> SREPanelStyle.MUTED;
        };
    }

    private static String chipSymbol(SwitchState value) {
        return switch (value) {
            case ON -> SYMBOL_ON;
            case OFF -> SYMBOL_OFF;
            case UNSET -> SYMBOL_UNSET;
        };
    }

    private static void drawChip(GuiGraphics g, Font font, int x, int y, int bg, int border, String symbol) {
        MinigameUI.roundRect(g, x, y, x + CHIP, y + CHIP, 3, bg);
        MinigameUI.roundBorder(g, x, y, x + CHIP, y + CHIP, 3, 1, border);
        g.drawCenteredString(font, Component.literal(symbol), x + CHIP / 2, y + 2, SREPanelStyle.TEXT);
    }
}

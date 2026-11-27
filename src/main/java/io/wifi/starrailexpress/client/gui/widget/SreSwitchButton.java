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

import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import io.wifi.starrailexpress.client.ui.SRETheme;
import io.wifi.starrailexpress.client.ui.UiSurface;
import io.wifi.starrailexpress.client.ui.UiText;
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
 * 右端是<b>胶囊形切换开关</b>（{@code docs/ui_style.md} §3）：轨道开 = 明亮黄铜、关 = 暗哑青铜灰，
 * 滑块是一枚银白小圆点。扁平画法，没有立体渐变，只靠「黄铜 × 深灰」的反差和滑块位置表达状态。
 * 三种状态的位置：开 = 滑块靠右、关 = 靠左、未设置 = 居中停在正中（一眼能看出「还没选」）。
 *
 * <p>
 * 标题有两种摆法：{@code label} 非空时「左标题 + 右开关」（按钮自带标题的行用，相邻的这种行会自动并排）；
 * 标题为空时把「开关 + 状态文字」居中（标题已经画在标签列上的行用）。
 */
@Environment(EnvType.CLIENT)
public class SreSwitchButton extends SreButton {

    /** 胶囊轨道宽。 */
    private static final int SWITCH_W = 18;
    /** 胶囊轨道高（与 {@link UiSurface#SWITCH_H} 一致）。 */
    private static final int SWITCH_H = UiSurface.SWITCH_H;
    /** 状态词与胶囊之间的间距。 */
    private static final int SWITCH_GAP = 6;
    /** 按钮内文字左边距。 */
    private static final int PAD = 5;
    /** 滑块过渡速度（每帧向目标位置靠近的比例）；瞬时状态本来就相等，不会有多余动画。 */
    private static final float KNOB_SPEED = 0.35F;

    private final Supplier<SwitchState> state;
    private final Function<SwitchState, Component> stateText;
    /** 滑块位置 0~1 的过渡值；负值表示还没初始化过。 */
    private float knobAnim = -1.0F;

    /**
     * @param label     按钮自带的标题；为 null 表示标题画在标签列上（按钮里只放「开关 + 状态文字」）
     * @param state     当前状态（{@link SwitchState#UNSET} = 未设置，滑块停在正中）
     * @param stateText 状态文字（可为 null，那就只画开关）；收 {@link SwitchState}，不用防 null
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
        return text.isEmpty() ? 0 : font.width(text) + SWITCH_W + SWITCH_GAP + PAD + 4;
    }

    /**
     * 一个就地的「是 / 否」开关（标题为空、状态画成胶囊 + 状态文字）：点一下切换，把新值交给 setter。
     *
     * <p>
     * 地图工具那种「一行一个设置、标签在标签列上」的地方用这个，之后自己摆位置（{@link #at}）。
     */
    public static SreSwitchButton toggle(Font font, boolean initial, Function<SwitchState, Component> stateText,
            Consumer<Boolean> setter) {
        return toggle(font, null, initial, stateText, setter);
    }

    /** 同上，但按钮自带标题（标题 + 状态词 + 胶囊都在按钮里）。 */
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

    /** 宽度：左边距 + 标题 + （状态词 + 间距）+ 胶囊 + 右边距（+ 余量），与 {@link #renderString} 对齐。 */
    private int naturalWidth(Font font) {
        int width = font.width(getMessage()) + stateWordWidth(font, state.get()) + SWITCH_GAP;
        // 三种状态里挑最宽的那个：切换状态时按钮宽度不会跳
        for (SwitchState candidate : SwitchState.values()) {
            width = Math.max(width, font.width(getMessage()) + stateWordWidth(font, candidate) + SWITCH_GAP);
        }
        return width + SWITCH_W + PAD + 6;
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
        // 右侧要给胶囊开关留位置
        return Math.max(8, getWidth() - SWITCH_W - SWITCH_GAP - 12);
    }

    @Override
    protected Component fullText() {
        // 标题在标签列上时这里只放状态文字，没有会被裁掉的内容
        return hasLabel() ? getMessage() : Component.empty();
    }

    @Override
    public void renderString(GuiGraphics g, Font font, int color) {
        SwitchState value = state.get();
        float knob = knobTarget(value);
        knobAnim = knobAnim < 0.0F ? knob : knobAnim + (knob - knobAnim) * KNOB_SPEED;
        if (Math.abs(knob - knobAnim) < 0.01F) {
            knobAnim = knob;
        }

        int switchY = getY() + (getHeight() - SWITCH_H) / 2;
        Component word = stateText == null ? null : stateText.apply(value);
        int wordWidth = word == null ? 0 : font.width(word) + SWITCH_GAP;
        int textY = getY() + (getHeight() - 8) / 2;

        if (hasLabel()) {
            int switchX = getX() + getWidth() - SWITCH_W - 4;
            UiSurface.switchTrack(g, switchX, switchY, SWITCH_W, SWITCH_H, value.isOn(), knobAnim, isHovered());
            // 状态词贴在开关左边（右对齐），标题被截断也不会盖住它
            if (word != null) {
                UiText.drawRight(g, font, word, switchX - SWITCH_GAP, textY, stateWordColor(value));
            }
            String shown = MapUiGraphics.clip(font, getMessage().getString(),
                    Math.max(8, textLimit() - wordWidth));
            UiText.draw(g, font, Component.literal(shown), getX() + PAD, textY, labelColor(value));
            return;
        }

        // 标题在标签列上的行：把「胶囊 + 状态文字」居中
        int groupWidth = SWITCH_W + wordWidth;
        int switchX = getX() + (getWidth() - groupWidth) / 2;
        UiSurface.switchTrack(g, switchX, switchY, SWITCH_W, SWITCH_H, value.isOn(), knobAnim, isHovered());
        if (word != null) {
            UiText.draw(g, font, word, switchX + SWITCH_W + SWITCH_GAP, textY, stateWordColor(value));
        }
    }

    private boolean hasLabel() {
        return !getMessage().getString().isEmpty();
    }

    /** 滑块目标位置：开 = 靠右、关 = 靠左、未设置 = 停在正中。 */
    private static float knobTarget(SwitchState value) {
        return switch (value) {
            case ON -> 1.0F;
            case OFF -> 0.0F;
            case UNSET -> 0.5F;
        };
    }

    /** 标题色：开 = 明亮黄铜；关 / 未设置 = 暗一档（悬停都提亮到高亮黄铜）。 */
    private static int labelColor(SwitchState value) {
        if (value.isOn()) {
            return SRETheme.TEXT;
        }
        return SRETheme.MUTED;
    }

    /** 状态词色：开 = 黄铜（与轨道呼应），其余 = 暗哑铜。 */
    private static int stateWordColor(SwitchState value) {
        return value.isOn() ? SRETheme.BRASS : SRETheme.MUTED;
    }
}

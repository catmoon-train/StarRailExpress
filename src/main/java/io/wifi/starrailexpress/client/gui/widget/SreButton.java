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

import io.wifi.starrailexpress.client.gui.screen.EditorLayout;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * SRE 的<b>普通按钮</b> —— 四个自定义工具编辑器（职业 / 修饰符 / 物品 / 方块）与地图工具共用这一份实现。
 *
 * <p>
 * 外观直接交给模组替换过的原版 widget 贴图
 * （{@code assets/minecraft/textures/gui/sprites/widget/button*.png}），所以它就是「其他 UI 里那种普通按钮」，
 * 不再自己画底色与强调色条。这里只多做两件事：
 * <ul>
 * <li>文字按可用宽度裁成省略号（{@code MapUiGraphics.clip}），不会溢出到相邻控件上；</li>
 * <li><b>真的裁到了</b>才挂「悬停看全文」的 tooltip —— 放得下就不挂，免得每个按钮都弹提示。</li>
 * </ul>
 *
 * <p>
 * 两种用法：
 * <ul>
 * <li>编辑器那种「布局统一摆位置」的地方：{@code new SreButton(font, 文字, 回调)}，
 * 之后由行排布 {@code setX/setY/setWidth}，排完调一次 {@link #refreshTooltip()}；</li>
 * <li>地图工具那种「就地写坐标」的地方：{@link #builder} 链式写法，见
 * {@code MapBuildHelperScreen} 与 {@code map_dev.modules} 里的用法。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class SreButton extends Button {

    /** 创建时给的字体（测量文字宽度用）；没给就取客户端字体。 */
    private final Font explicitFont;
    /**
     * 按钮<b>自带</b>的说明文字（不是「悬停看全文」那一条）。
     *
     * <p>
     * 用 {@link #setTooltipText(Component)} 设置。文字被裁掉时两者会<b>合并成一条</b>：
     * 全文 → 换行 → 自带说明。
     */
    private Component tooltipText;
    /** 当前 tooltip 是不是我们自己挂的（决定要不要清掉、以及状态有没有变）。 */
    private boolean tooltipOwned;
    private boolean appliedClipped;
    private Component appliedExtra;

    public SreButton(Component message, OnPress onPress) {
        this(null, message, onPress);
    }

    public SreButton(Font font, Component message, OnPress onPress) {
        super(0, 0, 120, EditorLayout.WIDGET_H, message, onPress, DEFAULT_NARRATION);
        this.explicitFont = font;
    }

    /** 测量用的字体：优先用创建时给的，没给就取客户端的。 */
    protected Font sreFont() {
        if (explicitFont != null) {
            return explicitFont;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.font;
    }

    /** 文字可用的宽度（子类画了别的东西时可以覆写）。 */
    protected int textLimit() {
        return Math.max(8, getWidth() - 6);
    }

    /** 按钮上实际要完整显示的文案（默认就是按钮文字）。 */
    protected Component fullText() {
        return getMessage();
    }

    /** 文字是否被裁掉了（裁掉才需要悬停看全文）。 */
    protected boolean isTextClipped() {
        Font measure = sreFont();
        Component full = fullText();
        if (measure == null || full == null || full.getString().isEmpty()) {
            return false;
        }
        return measure.width(full) > textLimit();
    }

    /**
     * 设按钮<b>自带</b>的说明文字（tooltip）。
     *
     * <p>
     * 文字放得下时就是这条说明；放不下时和「悬停看全文」合并成一条：
     * <b>全文 → 换行 → 自带说明</b>，这样鼠标移上去一次就能看全，不会互相顶掉。
     *
     * <p>
     * 想给按钮加 tooltip 请走这个方法（而不是 {@code setTooltip(Tooltip.create(...))}）：
     * {@link Tooltip} 里的文字读不出来，只有我们自己记住原文才能合并。
     */
    public void setTooltipText(Component text) {
        this.tooltipText = text;
        refreshTooltip();
    }

    /** 按钮自带的说明文字（没设过就是 null）。 */
    protected Component tooltipText() {
        return tooltipText;
    }

    @Override
    public void setTooltip(Tooltip tooltip) {
        super.setTooltip(tooltip);
        // 外部自己挂的 tooltip：不再算我们的（此后按新状态重挂）
        tooltipOwned = false;
        appliedClipped = false;
        appliedExtra = null;
    }

    /**
     * 按当前宽度刷新 tooltip。
     *
     * <p>
     * 状态没变时什么都不做（地图工具本来就不会逐处调用，所以 {@code renderWidget} 里也兜一次）。
     */
    public void refreshTooltip() {
        boolean clipped = isTextClipped();
        // 需要 tooltip 的两种情况：文字被裁了要「看全文」；或者按钮自带说明
        boolean needed = clipped || tooltipText != null;
        if (!needed && !tooltipOwned) {
            return; // 不归我们管：别动别人挂的 tooltip
        }
        if (tooltipOwned && appliedClipped == clipped && appliedExtra == tooltipText) {
            return; // 状态没变
        }
        Component wanted = buildTooltip(clipped);
        tooltipOwned = wanted != null;
        appliedClipped = clipped;
        appliedExtra = tooltipText;
        super.setTooltip(wanted == null ? null : Tooltip.create(wanted));
    }

    /** 合并规则：裁了 → 全文（+ 换行 + 自带说明）；没裁 → 自带说明。 */
    private Component buildTooltip(boolean clipped) {
        if (clipped && tooltipText != null) {
            return fullText().copy().append(Component.literal("\n")).append(tooltipText);
        }
        return clipped ? fullText() : tooltipText;
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        // 文字变了，是否放得下可能也变了
        appliedClipped = !appliedClipped;
        refreshTooltip();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 地图工具那边不会逐处调 refreshTooltip，这里兜一次（状态没变时是纯比较，很便宜）
        refreshTooltip();
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderString(GuiGraphics g, Font font, int color) {
        Component full = fullText();
        if (full == null || full.getString().isEmpty()) {
            return;
        }
        String shown = MapUiGraphics.clip(font, full.getString(), textLimit());
        g.drawCenteredString(font, Component.literal(shown), getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2, color);
    }

    // ══════════════════════════════════════════════════════════════════
    // 链式写法（地图工具这种「就地写坐标」的地方用）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 链式写法（地图工具那种「就地写坐标」的地方用）：
     * {@code SreButton.create(文字, 回调).bounds(x, y, w, h).build()}。
     *
     * <p>
     * 不叫 {@code builder} 是因为 {@link Button#builder(Component, Button.OnPress)} 已经占了这个名字
     * （它返回的是不带裁剪的普通 Button）。
     */
    public static SreButtonBuilder create(Component message, OnPress onPress) {
        return new SreButtonBuilder(message, onPress);
    }

    /** 与四个编辑器同一套按钮，只是允许像原来那样一行写完坐标。 */
    public static final class SreButtonBuilder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int w = 150;
        private int h = EditorLayout.WIDGET_H;

        private SreButtonBuilder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public SreButtonBuilder bounds(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            return this;
        }

        public SreButtonBuilder size(int w, int h) {
            this.w = w;
            this.h = h;
            return this;
        }

        public SreButtonBuilder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public SreButton build() {
            SreButton button = new SreButton(message, onPress);
            button.setX(x);
            button.setY(y);
            button.setWidth(w);
            button.setHeight(h);
            button.refreshTooltip();
            return button;
        }
    }
}

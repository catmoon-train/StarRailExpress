package org.agmas.noellesroles.client.widget.nodes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.awt.*;

public class LabelNodeWidget extends AbstractNodeWidget{
    public static class Builder<B extends Builder<B>> extends AbstractNodeWidget.Builder<B> {
        public Builder(int x, int y, int w, int h, Component component) {
            super(x, y, w, h, component);
            font = getDefaultFont();
            strColor = new Color(0xFFFFFFFF, true);
            interval = 0;
            m_bIsFillBg = true;
            m_bIsLineBg = true;
        }
        public B setFont(Font font) {
            this.font = font;
            return self();
        }
        public B setColor(Color color) {
            this.strColor = color;
            return self();
        }
        public B setInterval(int interval) {
            this.interval = interval;
            return self();
        }
        @Override
        public LabelNodeWidget build() {
            LabelNodeWidget labelNodeWidget = (LabelNodeWidget) super.build();
            labelNodeWidget.strColor = strColor;
            labelNodeWidget.font = font;
            labelNodeWidget.interval = interval;
            return labelNodeWidget;
        }
        @Override
        protected LabelNodeWidget create() {
            return new LabelNodeWidget(BG_FILL_COLOR, BG_LINE_COLOR, m_i32Order, x, y, w, h, component);
        }
        protected Font font;
        protected Color strColor;
        protected int interval;
    }
    protected LabelNodeWidget(int x, int y, int w, int h, Component component) {
        super(x, y, w, h, component);
    }
    protected LabelNodeWidget(Color bgFillColor, Color bgLineColor, int order, int x, int y, int w, int h, Component component) {
        super(bgFillColor, bgLineColor, order, x, y, w, h, component);
    }
    public static Builder<?> builder(int x, int y, int w, int h, Component component) {
        return new Builder<>(x, y, w, h, component);
    }
    @Override
    protected void renderSelf(GuiGraphics guiGraphics, int i, int j, float f) {
        super.renderSelf(guiGraphics, i, j, f);
        // 绘制指定颜色文本
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, (float) strColor.getAlpha() / 255.0F);
        renderScrollingString(guiGraphics, font, 2, strColor.getRGB());
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
    protected static Font getDefaultFont() {
        return Minecraft.getInstance().font;
    }
    protected Color strColor = new Color(0xFFFFFFFF, true);
    protected Font font = getDefaultFont();
    protected int interval = 0;
}

package org.agmas.noellesroles.client.widget.nodes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.awt.*;

/**
 * 滚动条类
 * <p>
 *     理论上能单独使用，但是没有意义，需要结合其他类进行使用
 * </p>
 */
public class ScrollBarNodeWidget extends AbstractNodeWidget{
    public static class Builder<B extends Builder<B>> extends AbstractNodeWidget.Builder<B> {
        public Builder(int x, int y, int w, int h, Component component) {
            super(x, y, w, h, component);
            BG_FILL_COLOR = new Color(0xAA000000, true);
            m_bIsFillBg = true;
        }
        public B setBarColor(Color color) {
            this.barColor = color;
            return self();
        }
        public B setBarWidth(int width) {
            this.barWidth = width;
            return self();
        }
        public B setListMode(NodeListWidget.ListMode mode) {
            this.m_emListMode = mode;
            return self();
        }
        public B setProcessCallBack(ProcessCallBack callBack) {
            this.processCallBack = callBack;
            return self();
        }
        @Override
        public ScrollBarNodeWidget build() {
            ScrollBarNodeWidget scrollBarNodeWidget = (ScrollBarNodeWidget) super.build();
            scrollBarNodeWidget.barColor = barColor;
            scrollBarNodeWidget.m_emListMode = m_emListMode;
            scrollBarNodeWidget.barLength = barWidth;
            if (processCallBack == null)
                processCallBack = (process) -> {};
            scrollBarNodeWidget.processCallBack = processCallBack;
            return scrollBarNodeWidget;
        }
        @Override
        protected ScrollBarNodeWidget create() {
            return new ScrollBarNodeWidget(x, y, w, h, component);
        }
        protected NodeListWidget.ListMode m_emListMode = NodeListWidget.ListMode.VERTICAL;
        protected ProcessCallBack processCallBack = null;
        protected Color barColor = new Color(0xFFAAAAAA, true);
        protected int barWidth = 5;
    }
    public static interface ProcessCallBack {
        void onProcess(float process);
    }
    protected ScrollBarNodeWidget(int x, int y, int w, int h, Component component) {
        super(x, y, w, h, component);
    }
    public static Builder<?> builder(int x, int y, int w, int h, Component component) {
        return new Builder<>(x, y, w, h, component);
    }
    public void setProcess(float process) {
        if (process < 0) {
            process = 0;
        }
        else if (process > 1) {
            process = 1;
        }
        this.process = process;
    }
    public void setBarLength(int length) {
        if (length < 0) {
            length = 0;
        }
        switch (m_emListMode) {
            case NodeListWidget.ListMode.HORIZONTAL:
                length = Math.min(width, length);
                break;
            case NodeListWidget.ListMode.VERTICAL:
                length = Math.min(height, length);
                break;
        }
        this.barLength = length;
    }
    public void setListMode(NodeListWidget.ListMode mode) {
        m_emListMode = mode;
    }
    public int getMoveableLen() {
        return switch (m_emListMode) {
            case VERTICAL -> height - barLength;
            case HORIZONTAL -> width - barLength;
        };
    }
    protected void moveProcessTo(float process) {
        setProcess(process);
        if (processCallBack != null)
            processCallBack.onProcess(process);
    }

    @Override
    protected boolean canRelease() {
        return isDragging;
    }
    @Override
    public void onRelease(double d, double e) {
        super.onRelease(d, e);
        if (isDragging)
            isDragging = false;
    }
    @Override
    protected boolean canDrag(double d, double e, int i, double f, double g) {
        return isMouseOver(d, e) || isDragging;
    }
    @Override
    protected void onDrag(double d, double e, double f, double g) {
        super.onDrag(d, e, f, g);
        isDragging = true;
        float delta = switch (m_emListMode) {
            case HORIZONTAL -> (float) f;
            case VERTICAL -> (float) g;
        };
        moveProcessTo(process + delta / getMoveableLen());
    }

    @Override
    protected void renderSelf(GuiGraphics guiGraphics, int i, int j, float f) {
        super.renderSelf(guiGraphics, i, j, f);
        int offsetX = 0;
        int offsetY = 0;
        int deltaX = width;
        int deltaY = height;
        switch (m_emListMode) {
            case VERTICAL:
                offsetY = (int) (process * (height - barLength));
                deltaY = barLength;
                if (offsetY > height) {
                    offsetY = height;
                }
                if (offsetY + deltaY > + height) {
                    deltaY = height - offsetY;
                }
                break;
            case HORIZONTAL:
                offsetX = (int) (process * (width - barLength));
                deltaX = barLength;
                if (offsetX > width) {
                    offsetX = width;
                }
                if (offsetX + deltaX > width) {
                    deltaX = width - offsetX;
                }
                break;
        }
        int x1 = getX() + offsetX;
        int y1 = getY() + offsetY;
        guiGraphics.fill(x1, y1, x1 + deltaX, y1 + deltaY, barColor.getRGB());
    }
    /** 滚动条的移动方向 */
    protected NodeListWidget.ListMode m_emListMode = NodeListWidget.ListMode.VERTICAL;
    protected ProcessCallBack processCallBack;
    protected Color barColor = new Color(0xFFAAAAAA, true);
    protected boolean isDragging = false;
    protected float process = 0;
    protected int barLength = 5;
}

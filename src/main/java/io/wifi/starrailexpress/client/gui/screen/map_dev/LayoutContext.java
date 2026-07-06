package io.wifi.starrailexpress.client.gui.screen.map_dev;

import net.minecraft.client.gui.Font;

public class LayoutContext {
    public final int panelLeftX, panelTopY;
    public final int panelWidth, panelHeight;
    public final int contentStartY;
    public final int contentEndY;
    public final int gutter;
    public final Font font;

    public LayoutContext(int panelLeftX, int panelTopY, int panelWidth, int panelHeight,
            int contentStartY, int contentEndY, int gutter, Font font) {
        this.panelLeftX = panelLeftX;
        this.panelTopY = panelTopY;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.contentStartY = contentStartY;
        this.contentEndY = contentEndY;
        this.gutter = gutter;
        this.font = font;
    }

    public int contentWidth() {
        return panelWidth - gutter * 2;
    }

    public int columnWidth(int columns, int gap) {
        return (contentWidth() - gap * (columns - 1)) / columns;
    }

    public int leftColumnX() {
        return panelLeftX + gutter;
    }

    public int rightColumnX(int columns, int gap) {
        return leftColumnX() + columnWidth(columns, gap) + gap;
    }
}
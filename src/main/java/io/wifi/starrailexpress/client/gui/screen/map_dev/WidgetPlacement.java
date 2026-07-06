package io.wifi.starrailexpress.client.gui.screen.map_dev;

import net.minecraft.client.gui.components.AbstractWidget;

public class WidgetPlacement {
    public final AbstractWidget widget;
    public final int relativeY; // 相对于内容起始 Y 的偏移

    public WidgetPlacement(AbstractWidget widget, int relativeY) {
        this.widget = widget;
        this.relativeY = relativeY;
    }
}
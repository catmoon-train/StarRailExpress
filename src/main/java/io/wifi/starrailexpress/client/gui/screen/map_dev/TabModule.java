package io.wifi.starrailexpress.client.gui.screen.map_dev;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.List;

public interface TabModule {
    Component getTabTitle();
    default Component getTabFullTitle(){
        return getTabTitle();
    }
    void init(LayoutContext layout, ModuleContext context, List<WidgetPlacement> placements);
    int getContentHeight();
    default void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
}
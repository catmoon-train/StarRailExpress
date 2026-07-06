package io.wifi.starrailexpress.client.gui.screen.map_dev.modules;

import io.wifi.starrailexpress.client.gui.screen.map_dev.*;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;
import java.util.List;

public class AreasModule implements TabModule {
    private static final String[] AREA_KEYS = { "readyArea", "playArea", "sceneArea", "resetTemplateArea",
            "resetPasteArea" };

    @Override
    public Component getTabTitle() {
        return Component.translatable("sre.map_helper.tab.areas");
    }

    @Override
    public void init(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements) {
        int y = 0, gap = 10, bh = 22;
        int bw = layout.columnWidth(2, gap);
        int leftX = layout.leftColumnX(), rightX = layout.rightColumnX(2, gap);

        for (int i = 0; i < AREA_KEYS.length; i++) {
            final String cmd = AREA_KEYS[i];
            Component areaName = Component.translatable("sre.area." + cmd);
            int rowY = y + i * (bh + gap);

            placements.add(new WidgetPlacement(
                    ModernButton
                            .builder(Component.translatable("sre.map_helper.area.set_min", areaName),
                                    b -> ctx.sendAndClose(String.format("sre:area_manager set %s min %.0f %.0f %.0f",
                                            cmd, Math.floor(ctx.ax()), Math.floor(ctx.ay()), Math.floor(ctx.az()))))
                            .bounds(leftX, rowY, bw, bh).accentBar(AccentSide.LEFT).build(),
                    rowY));
            placements.add(new WidgetPlacement(
                    ModernButton
                            .builder(Component.translatable("sre.map_helper.area.set_max", areaName),
                                    b -> ctx.sendAndClose(String.format("sre:area_manager set %s max %.0f %.0f %.0f",
                                            cmd, Math.floor(ctx.ax()), Math.floor(ctx.ay()), Math.floor(ctx.az()))))
                            .bounds(rightX, rowY, bw, bh).accentBar(AccentSide.RIGHT).build(),
                    rowY));
        }
    }

    @Override
    public int getContentHeight() {
        return AREA_KEYS.length * 32;
    }
}
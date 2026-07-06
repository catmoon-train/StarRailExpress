package io.wifi.starrailexpress.client.gui.screen.map_dev.modules;

import io.wifi.starrailexpress.client.gui.screen.map_dev.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;
import java.util.List;

public class EnvironmentModule implements TabModule {
    @Override public Component getTabTitle() { return Component.translatable("sre.map_helper.tab.environment"); }

    @Override
    public void init(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements) {
        int y = 0, gap = 10, bh = 22, fw = Math.min(120, layout.contentWidth() - 80);
        int leftX = layout.leftColumnX();

        EditBox effectBox = new EditBox(layout.font, leftX, y, fw, bh, Component.empty());
        effectBox.setMaxLength(128);
        placements.add(new WidgetPlacement(effectBox, y));

        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.set_effect"), b -> {
                    String val = effectBox.getValue().trim();
                    ctx.sendOnly("sre:area_manager set effect " + (val.isEmpty() ? "\"\"" : val));
                }).bounds(leftX + fw + gap, y, layout.contentWidth() - fw - gap, bh).accentBar(AccentSide.RIGHT).build(), y));
    }

    @Override public int getContentHeight() { return 32; }
}
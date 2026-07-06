package io.wifi.starrailexpress.client.gui.screen.map_dev.modules;

import io.wifi.starrailexpress.client.gui.screen.map_dev.*;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;
import java.util.List;

public class BooleanSettingsModule implements TabModule {
    private static final String[] FIELDS = { "noReset", "haveOutsideSound", "sceneOffsetEnabled", "mustCopy",
            "minigameQuestEnabled" };
    private static final String[] FIELD_KEYS = {
            "sre.field.noReset", "sre.field.haveOutsideSound", "sre.field.sceneOffsetEnabled", "sre.field.mustCopy",
            "sre.field.minigameQuestEnabled"
    };

    @Override
    public Component getTabTitle() {
        return Component.translatable("sre.map_helper.tab.settings");
    }

    @Override
    public void init(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements) {
        int y = 0, gap = 10, bh = 22;
        int bw = layout.columnWidth(2, gap);
        int leftX = layout.leftColumnX(), rightX = layout.rightColumnX(2, gap);

        for (int i = 0; i < FIELDS.length; i++) {
            final String field = FIELDS[i];
            Component label = Component.translatable(FIELD_KEYS[i]);
            int rowY = y + i * (bh + gap);

            placements.add(new WidgetPlacement(
                    ModernButton
                            .builder(Component.translatable("sre.map_helper.set_true", label),
                                    b -> ctx.sendOnly("sre:area_manager set " + field + " true"))
                            .bounds(leftX, rowY, bw, bh).accentBar(AccentSide.LEFT).build(),
                    rowY));
            placements.add(new WidgetPlacement(
                    ModernButton
                            .builder(Component.translatable("sre.map_helper.set_false", label),
                                    b -> ctx.sendOnly("sre:area_manager set " + field + " false"))
                            .bounds(rightX, rowY, bw, bh).accentBar(AccentSide.RIGHT).build(),
                    rowY));
        }
    }

    @Override
    public int getContentHeight() {
        return FIELDS.length * 32;
    }
}
package io.wifi.starrailexpress.client.gui.screen.map_dev.modules;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.map_dev.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;
import java.util.List;

public class MapModule implements TabModule {
    private EditBox mapNameBox, mapImportBox, initialItemsBox;

    @Override
    public Component getTabTitle() {
        return Component.translatable("sre.map_helper.tab.map");
    }

    @Override
    public void init(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements) {
        int y = 0, gap = 10, bh = 22;
        int leftX = layout.leftColumnX(), rightX = layout.rightColumnX(2, gap);
        int fullWidth = layout.contentWidth();
        AreasWorldComponent areas = SREClient.areaComponent;
        String currentName = areas == null || areas.mapName == null ? "" : areas.mapName;

        mapNameBox = new EditBox(layout.font, leftX, y, fullWidth, bh,
                Component.translatable("sre.map_helper.map_name"));
        mapNameBox.setMaxLength(128);
        mapNameBox.setValue(currentName);
        placements.add(new WidgetPlacement(mapNameBox, y));

        int row1 = y + bh + gap;
        placements.add(
                new WidgetPlacement(ModernButton.builder(Component.translatable("sre.map_helper.save_as_new"), b -> {
                    String name = mapNameBox.getValue().trim();
                    if (!name.isEmpty())
                        ctx.sendOnly("sre:area_manager map save " + ctx.quoteCommandArgument(name));
                }).bounds(leftX, row1, layout.columnWidth(2, gap), bh).accentBar(AccentSide.LEFT).build(), row1));
        placements.add(
                new WidgetPlacement(ModernButton.builder(Component.translatable("sre.map_helper.save_overwrite"), b -> {
                    String name = mapNameBox.getValue().trim();
                    if (!name.isEmpty())
                        ctx.sendOnly("sre:area_manager map save " + ctx.quoteCommandArgument(name) + " force");
                }).bounds(rightX, row1, layout.columnWidth(2, gap), bh).accentBar(AccentSide.RIGHT).build(), row1));

        int row2 = row1 + bh + gap;
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.load_map_config"), b -> {
                    String name = mapNameBox.getValue().trim();
                    if (!name.isEmpty())
                        ctx.sendOnly("sre:area_manager map load " + ctx.quoteCommandArgument(name));
                }).bounds(leftX, row2, layout.columnWidth(2, gap), bh).accentBar(AccentSide.LEFT).build(), row2));
        placements.add(new WidgetPlacement(
                ModernButton
                        .builder(Component.translatable("sre.map_helper.list_maps"),
                                b -> ctx.sendOnly("sre:area_manager map list"))
                        .bounds(rightX, row2, layout.columnWidth(2, gap), bh).accentBar(AccentSide.RIGHT).build(),
                row2));

        int row3 = row2 + bh + gap;
        mapImportBox = new EditBox(layout.font, leftX, row3, fullWidth, bh,
                Component.translatable("sre.map_helper.import_filename_hint"));
        mapImportBox.setMaxLength(128);
        placements.add(new WidgetPlacement(mapImportBox, row3));

        int row4 = row3 + bh + gap;
        placements
                .add(new WidgetPlacement(
                        ModernButton
                                .builder(Component.translatable("sre.map_helper.import_as_map"),
                                        b -> importMap(ctx, false))
                                .bounds(leftX, row4, layout.columnWidth(2, gap), bh).accentBar(AccentSide.LEFT).build(),
                        row4));
        placements.add(new WidgetPlacement(
                ModernButton
                        .builder(Component.translatable("sre.map_helper.import_overwrite"), b -> importMap(ctx, true))
                        .bounds(rightX, row4, layout.columnWidth(2, gap), bh).accentBar(AccentSide.RIGHT).build(),
                row4));

        int row5 = row4 + bh + gap;
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.create_blank_map"), b -> {
                    ctx.sendOnly("sre:area_manager create_new");
                    String name = mapNameBox.getValue().trim();
                    if (!name.isEmpty())
                        ctx.sendOnly("sre:area_manager map name " + ctx.quoteCommandArgument(name));
                }).bounds(leftX, row5, fullWidth, bh).accentBar(AccentSide.BOTTOM).build(), row5));

        int row6 = row5 + bh + gap;
        String currentInitialItems = "";
        if (areas != null && !areas.initialItems.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String item : areas.initialItems) {
                String[] parts = item.split(";");
                if (parts.length >= 2)
                    sb.append(sb.length() > 0 ? "," : "").append(parts[0]).append(",").append(parts[1]);
            }
            currentInitialItems = sb.toString();
        }
        initialItemsBox = new EditBox(layout.font, leftX, row6, fullWidth, bh,
                Component.translatable("sre.map_helper.initial_items_hint"));
        initialItemsBox.setMaxLength(512);
        initialItemsBox.setValue(currentInitialItems);
        placements.add(new WidgetPlacement(initialItemsBox, row6));

        int row7 = row6 + bh + gap;
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.set_initial_items"), b -> {
                    String value = initialItemsBox.getValue().trim();
                    if (!value.isEmpty())
                        ctx.sendOnly("sre:area_manager set initialItems " + ctx.quoteCommandArgument(value));
                }).bounds(leftX, row7, fullWidth, bh).accentBar(AccentSide.BOTTOM).build(), row7));
    }

    private void importMap(ModuleContext ctx, boolean force) {
        String filename = mapImportBox.getValue().trim();
        String mapName = mapNameBox.getValue().trim();
        if (filename.isEmpty() || mapName.isEmpty())
            return;
        ctx.sendOnly("sre:area_manager map import " + ctx.quoteCommandArgument(filename) + " as "
                + ctx.quoteCommandArgument(mapName) + (force ? " force" : ""));
    }

    @Override
    public int getContentHeight() {
        return 8 * 32;
    }
}
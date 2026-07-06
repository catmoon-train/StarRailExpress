package io.wifi.starrailexpress.client.gui.screen.map_dev.modules;

import io.wifi.starrailexpress.client.gui.screen.map_dev.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;
import java.util.List;

public class RoomsModule implements TabModule {
    @Override
    public Component getTabTitle() {
        return Component.translatable("sre.map_helper.tab.rooms");
    }

    @Override
    public void init(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements) {
        int y = 0, gap = 10, bh = 22, fw = 60;
        int leftX = layout.leftColumnX();

        EditBox roomCountBox = new EditBox(layout.font, leftX, y, fw, bh, Component.empty());
        roomCountBox.setValue("0");
        roomCountBox.setMaxLength(20);
        placements.add(new WidgetPlacement(roomCountBox, y));

        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.set_room_count"), b -> {
                    String count = roomCountBox.getValue().trim();
                    if (!count.isEmpty())
                        ctx.sendOnly("sre:area_manager set roomCount " + count);
                }).bounds(leftX + fw + gap, y, layout.columnWidth(1, 0) - fw - gap, bh).accentBar(AccentSide.LEFT)
                        .build(),
                y));

        int row2 = y + bh + gap;
        EditBox roomIdBox = new EditBox(layout.font, leftX, row2, fw, bh, Component.empty());
        roomIdBox.setValue("0");
        roomIdBox.setMaxLength(20);
        placements.add(new WidgetPlacement(roomIdBox, row2));

        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.add_to_room"), b -> {
                    String idStr = roomIdBox.getValue().trim();
                    if (!idStr.isEmpty()) {
                        try {
                            int id = Integer.parseInt(idStr);
                            ctx.sendOnly(String.format("sre:area_manager set roomPositions add %d %d %d %d", id,
                                    (long) Math.floor(ctx.ax()), (long) Math.floor(ctx.ay()),
                                    (long) Math.floor(ctx.az())));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }).bounds(leftX + fw + gap, row2, layout.columnWidth(1, 0) - fw - gap, bh).accentBar(AccentSide.LEFT)
                        .build(),
                row2));

        int row3 = row2 + bh + gap;
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.remove_room"), b -> {
                    String idStr = roomIdBox.getValue().trim();
                    if (!idStr.isEmpty()) {
                        try {
                            int id = Integer.parseInt(idStr);
                            ctx.sendOnly("sre:area_manager set roomPositions remove " + id);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }).bounds(leftX + fw + gap, row3, layout.columnWidth(1, 0) - fw - gap, bh).accentBar(AccentSide.RIGHT)
                        .build(),
                row3));
    }

    @Override
    public int getContentHeight() {
        return 3 * 32;
    }
}
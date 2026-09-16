/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.gui.screen.map_dev.modules;

import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.widget.SreButton;
import io.wifi.starrailexpress.client.gui.screen.map_dev.*;
import net.minecraft.client.gui.components.EditBox;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
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

        // 初值取当前配置（而不是写死的 "0"），用户才看得到现在设的是几间房
        EditBox roomCountBox = new EditBox(layout.font, leftX, y, fw, bh, Component.empty());
        roomCountBox.setMaxLength(20);
        roomCountBox.setValue(String.valueOf(currentRoomCount()));
        roomCountBox.setHint(SREPanelStyle.hint(Component.translatable("sre.map_helper.set_room_count")));
        roomCountBox.setTooltip(Tooltip.create(Component.translatable("sre.map_helper.set_room_count")));
        placements.add(new WidgetPlacement(roomCountBox, y));

        placements.add(new WidgetPlacement(
                SreButton.create(Component.translatable("sre.map_helper.set_room_count"), b -> {
                    String count = roomCountBox.getValue().trim();
                    if (!count.isEmpty())
                        ctx.sendOnly("sre:area_manager set roomCount " + count);
                }).bounds(leftX + fw + gap, y, layout.columnWidth(1, 0) - fw - gap, bh)
                        .build(),
                y));

        int row2 = y + bh + gap;
        // 房间序号：提示里写清楚填什么（0 开始）
        EditBox roomIdBox = new EditBox(layout.font, leftX, row2, fw, bh, Component.empty());
        roomIdBox.setMaxLength(20);
        roomIdBox.setValue("0");
        roomIdBox.setHint(SREPanelStyle.hint(Component.translatable("sre.map_helper.room_id_hint")));
        roomIdBox.setTooltip(Tooltip.create(Component.translatable("sre.map_helper.room_id_hint")));
        placements.add(new WidgetPlacement(roomIdBox, row2));

        placements.add(new WidgetPlacement(
                SreButton.create(Component.translatable("sre.map_helper.add_to_room"), b -> {
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
                }).bounds(leftX + fw + gap, row2, layout.columnWidth(1, 0) - fw - gap, bh)
                        .build(),
                row2));

        int row3 = row2 + bh + gap;
        placements.add(new WidgetPlacement(
                SreButton.create(Component.translatable("sre.map_helper.remove_room"), b -> {
                    String idStr = roomIdBox.getValue().trim();
                    if (!idStr.isEmpty()) {
                        try {
                            int id = Integer.parseInt(idStr);
                            ctx.sendOnly("sre:area_manager set roomPositions remove " + id);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }).bounds(leftX + fw + gap, row3, layout.columnWidth(1, 0) - fw - gap, bh)
                        .build(),
                row3));
    }

    /** 当前配置里已设的房间数量（读不到时回退 1）。 */
    private static int currentRoomCount() {
        AreasWorldComponent areas = SREClient.areaComponent;
        return areas == null ? 1 : areas.getRoomCount();
    }

    @Override
    public int getContentHeight() {
        return 3 * 32;
    }
}
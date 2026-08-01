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

import io.wifi.starrailexpress.client.gui.screen.map_dev.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;

import java.util.List;

/**
 * 地图配置 GUI「会议」标签页：紧急会议系统（右键尸体召开会议）的可视化配置。
 * 对应 {@code AreasSettings.meeting*} 字段，见 {@code net.exmo.sre.meeting.MeetingManager}。
 */
public class MeetingModule implements TabModule {

    @Override
    public Component getTabTitle() {
        return Component.translatable("sre.map_helper.tab.meeting");
    }

    @Override
    public void init(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements) {
        int gap = 10, bh = 22, rowStep = bh + gap;
        int bw = layout.columnWidth(2, gap);
        int leftX = layout.leftColumnX(), rightX = layout.rightColumnX(2, gap);
        int fullW = layout.contentWidth();
        int y = 0;

        // 启用 / 禁用
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.meeting.enable"),
                        b -> ctx.sendOnly("sre:area_manager set meetingEnabled true"))
                        .bounds(leftX, y, bw, bh).accentBar(AccentSide.LEFT).build(),
                y));
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.meeting.disable"),
                        b -> ctx.sendOnly("sre:area_manager set meetingEnabled false"))
                        .bounds(rightX, y, bw, bh).accentBar(AccentSide.RIGHT).build(),
                y));
        y += rowStep;

        // 在当前（应用偏移后）位置设置会议地点
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.meeting.set_point"),
                        b -> {
                            ctx.sendOnly(String.format("sre:area_manager set meetingPosition.x %f", ctx.ax()));
                            ctx.sendOnly(String.format("sre:area_manager set meetingPosition.y %f", ctx.ay()));
                            ctx.sendAndClose(String.format("sre:area_manager set meetingPosition.z %f", ctx.az()));
                        })
                        .bounds(leftX, y, fullW, bh).accentBar(AccentSide.BOTTOM).build(),
                y));
        y += rowStep;

        // 数值项：椅子搜寻半径不再支持（因为是软定义的AABB，相对坐标的AABB） / 讨论时长（秒）/ 冷却（秒）
        y = addNumberRow(placements, layout, ctx, y,
                "sre.map_helper.meeting.discuss_seconds", "meetingDiscussSeconds", "60");
        addNumberRow(placements, layout, ctx, y,
                "sre.map_helper.meeting.cooldown_seconds", "meetingCooldownSeconds", "90");
    }

    private int addNumberRow(List<WidgetPlacement> placements, LayoutContext layout, ModuleContext ctx,
            int y, String labelKey, String field, String defaultValue) {
        int gap = 10, bh = 22;
        int bw = layout.columnWidth(2, gap);
        int leftX = layout.leftColumnX(), rightX = layout.rightColumnX(2, gap);

        EditBox box = new EditBox(layout.font, leftX, y, bw, bh, Component.empty());
        box.setValue(defaultValue);
        box.setMaxLength(10);
        box.setHint(Component.translatable(labelKey));
        placements.add(new WidgetPlacement(box, y));

        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.meeting.apply",
                                Component.translatable(labelKey)),
                        b -> ctx.sendOnly("sre:area_manager set " + field + " " + box.getValue().trim()))
                        .bounds(rightX, y, bw, bh).accentBar(AccentSide.RIGHT).build(),
                y));
        return y + bh + gap;
    }

    @Override
    public int getContentHeight() {
        return 5 * 32;
    }
}

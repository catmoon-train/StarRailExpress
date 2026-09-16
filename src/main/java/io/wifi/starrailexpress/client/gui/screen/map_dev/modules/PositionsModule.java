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
import io.wifi.starrailexpress.client.gui.widget.SreButton;
import net.minecraft.network.chat.Component;
import java.util.List;

public class PositionsModule implements TabModule {
    @Override
    public Component getTabTitle() {
        return Component.translatable("sre.map_helper.tab.positions");
    }

    @Override
    public void init(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements) {
        int y = 0, gap = 10, bh = 22;
        int bw = layout.columnWidth(2, gap);
        int leftX = layout.leftColumnX(), rightX = layout.rightColumnX(2, gap);

        // 坐标与朝向按「面板头部显示的那种紧凑格式」输出，不出现 12.300000 这种长尾巴
        placements.add(new WidgetPlacement(
                SreButton
                        .builder(Component.translatable("sre.map_helper.set_spawn"),
                                b -> ctx.sendAndClose(String.format("sre:area_manager set spawnPos %s %s %s %.1f %.1f",
                                        num(ctx.ax()), num(ctx.ay()), num(ctx.az()), ctx.playerYaw(),
                                        ctx.playerPitch())))
                        .bounds(leftX, y, bw, bh).build(),
                y));
        placements.add(new WidgetPlacement(
                SreButton.create(Component.translatable("sre.map_helper.set_spectator_spawn"),
                        b -> ctx.sendAndClose(
                                String.format("sre:area_manager set spectatorSpawnPos %s %s %s %.1f %.1f",
                                        num(ctx.ax()), num(ctx.ay()), num(ctx.az()), ctx.playerYaw(),
                                        ctx.playerPitch())))
                        .bounds(rightX, y, bw, bh).build(),
                y));
    }

    /** 数值格式：整数不带小数点，其余最多 4 位小数（与头部坐标显示一致）。 */
    private static String num(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e9) {
            return String.valueOf((long) v);
        }
        return String.format("%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override
    public int getContentHeight() {
        return 32;
    }
}
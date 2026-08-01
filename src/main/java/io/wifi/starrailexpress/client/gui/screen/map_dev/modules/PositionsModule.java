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
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;
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

        placements.add(new WidgetPlacement(
                ModernButton
                        .builder(Component.translatable("sre.map_helper.set_spawn"),
                                b -> ctx.sendAndClose(String.format("sre:area_manager set spawnPos %f %f %f %.1f %.1f",
                                        ctx.ax(), ctx.ay(), ctx.az(), ctx.playerYaw(), ctx.playerPitch())))
                        .bounds(leftX, y, bw, bh).accentBar(AccentSide.LEFT).build(),
                y));
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.set_spectator_spawn"),
                        b -> ctx.sendAndClose(String.format("sre:area_manager set spectatorSpawnPos %f %f %f %.1f %.1f",
                                ctx.ax(), ctx.ay(), ctx.az(), ctx.playerYaw(), ctx.playerPitch())))
                        .bounds(rightX, y, bw, bh).accentBar(AccentSide.RIGHT).build(),
                y));
    }

    @Override
    public int getContentHeight() {
        return 32;
    }
}
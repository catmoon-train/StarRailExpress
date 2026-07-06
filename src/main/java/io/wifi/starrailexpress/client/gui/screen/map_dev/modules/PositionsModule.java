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
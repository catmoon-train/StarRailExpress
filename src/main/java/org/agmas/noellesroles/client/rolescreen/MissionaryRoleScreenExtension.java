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

package org.agmas.noellesroles.client.rolescreen;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.RoleScreenHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.MissionaryPlayerWidget;
import org.agmas.noellesroles.role.ModMeetingRoles;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 传教士背包界面扩展：在背包界面列出可传教的玩家。
 */
public final class MissionaryRoleScreenExtension extends PlayerListRoleScreenExtension<PlayerInfo> {
    public static final MissionaryRoleScreenExtension INSTANCE = new MissionaryRoleScreenExtension();

    private MissionaryRoleScreenExtension() {
    }

    @Override
    protected RoleScreenHelper<PlayerInfo> createHelper(LocalPlayer player) {
        RoleScreenHelper<PlayerInfo> h = new RoleScreenHelper<>(
                player,
                ModMeetingRoles.MISSIONARY,
                this::createMissionaryWidget,
                TEXT_PROVIDER,
                this::drawMissionarySelectionHint,
                this::getEligiblePlayers);
        h.setNameExtractor(info -> info.getProfile().getName());
        return h;
    }

    private Button createMissionaryWidget(LimitedInventoryScreen screen, int x, int y, PlayerInfo playerEntity,
            int index) {
        MissionaryPlayerWidget widget = new MissionaryPlayerWidget(screen, x, y, playerEntity);
        screen.addRoleWidget(widget);
        return widget;
    }

    private void drawMissionarySelectionHint(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.missionary.player_selection");
        int color = Color.PINK.getRGB();

        int textWidth = client.font.width(text);
        context.drawString(client.font, text,
                point.x - textWidth / 2, point.y + 40, color);
    }

    private List<PlayerInfo> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return List.of();
        }
        return client.getConnection().getListedOnlinePlayers().stream()
                .filter(a -> a.getProfile().getId() != client.player.getUUID())
                .collect(Collectors.toList());
    }
}

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
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

import org.agmas.noellesroles.client.widget.DoremyPlayerWidget;
import org.agmas.noellesroles.role.touhou.THMiscRoles;

import java.awt.Color;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 四季映姬背包界面扩展：在背包界面列出可审判的玩家（UUID 列表）。
 */
public final class DoremyRoleScreenExtension extends PlayerListRoleScreenExtension<UUID> {

    public DoremyRoleScreenExtension() {
    }

    @Override
    protected RoleScreenHelper<UUID> createHelper(LocalPlayer player) {
        RoleScreenHelper<UUID> h = new RoleScreenHelper<>(
                player,
                THMiscRoles.DOREMY,
                this::createDoremyWidget,
                TEXT_PROVIDER,
                this::drawVoodooTip,
                this::getEligiblePlayers);
        h.setNameExtractor(this::playerNameOf);
        return h;
    }

    private String playerNameOf(UUID playerUUID) {
        PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(playerUUID);
        return info == null ? "" : info.getProfile().getName();
    }

    private DoremyPlayerWidget createDoremyWidget(LimitedInventoryScreen screen, int x, int y, UUID playerUUID,
            int index) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }

        PlayerInfo playerListEntry = client.player.connection.getPlayerInfo(playerUUID);
        if (playerListEntry == null) {
            return null;
        }

        DoremyPlayerWidget widget = new DoremyPlayerWidget(
                screen, x, y, playerUUID, playerListEntry, client.player.level(), index);
        screen.addRoleWidget(widget);
        return widget;
    }

    private void drawVoodooTip(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        {
            Component text = Component.translatable("hud.doremy.tip");
            int textWidth = client.font.width(text);
            context.drawString(client.font, text,
                    point.x - textWidth / 2, point.y + 40, Color.RED.getRGB());
        }
    }

    private List<UUID> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return List.of();
        }

        return client.player.connection.getOnlinePlayers().stream()
                .filter(info -> {
                    if (info == null)
                        return false;
                    if (info.getProfile() == null)
                        return false;
                    if (info.getGameMode() != GameType.ADVENTURE) {
                        return false;
                    }
                    return true;
                })
                .map((info) -> info.getProfile().getId())
                .collect(Collectors.toList());
    }
}

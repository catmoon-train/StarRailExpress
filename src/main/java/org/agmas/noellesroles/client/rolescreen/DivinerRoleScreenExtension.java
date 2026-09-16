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

import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.RoleScreenHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.DivinerPlayerWidget;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.Color;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 占卜家背包界面扩展：列出本局参与者，点击头像选中占卜目标。
 *
 * <p>
 * 与巫毒师不同的是：中途加入（未参与本局）的玩家不会出现在列表里。
 */
public final class DivinerRoleScreenExtension extends PlayerListRoleScreenExtension<UUID> {

    public DivinerRoleScreenExtension() {
    }

    @Override
    protected RoleScreenHelper<UUID> createHelper(LocalPlayer player) {
        RoleScreenHelper<UUID> h = new RoleScreenHelper<>(
                player,
                ModRoles.DIVINER,
                this::createDivinerWidget,
                TEXT_PROVIDER,
                this::drawDivinerTip,
                this::getEligiblePlayers);
        h.setNameExtractor(this::playerNameOf);
        return h;
    }

    private String playerNameOf(UUID playerUUID) {
        PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(playerUUID);
        return info == null ? "" : info.getProfile().getName();
    }

    private DivinerPlayerWidget createDivinerWidget(LimitedInventoryScreen screen, int x, int y, UUID playerUUID,
            int index) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }

        PlayerInfo playerListEntry = client.player.connection.getPlayerInfo(playerUUID);
        if (playerListEntry == null) {
            return null;
        }

        DivinerPlayerWidget widget = new DivinerPlayerWidget(
                screen, x, y, playerUUID, playerListEntry, client.player.level(), index);
        screen.addRoleWidget(widget);
        return widget;
    }

    private void drawDivinerTip(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        Component text = Component.translatable("hud.diviner.tip");
        int textWidth = client.font.width(text);
        context.drawString(client.font, text, point.x - textWidth / 2, point.y + 40, Color.WHITE.getRGB());
    }

    private List<UUID> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return List.of();
        }

        ParticipationComponent participation = ParticipationComponent.KEY.get(client.player.level());
        return client.player.connection.getOnlinePlayerIds().stream()
                .filter(uuid -> !uuid.equals(client.player.getUUID()))
                .filter(uuid -> {
                    PlayerInfo entry = client.player.connection.getPlayerInfo(uuid);
                    return entry != null;
                })
                // 与巫毒师不同：中途加入（未参与本局）的玩家不显示
                .filter(participation::isParticipating)
                .collect(Collectors.toList());
    }
}

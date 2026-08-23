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

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.RoleScreenHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.client.widget.AmonPlayerWidget;
import org.agmas.noellesroles.role_data.neutral.AmonRoleData;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 阿蒙背包界面扩展：在背包界面列出可夺舍的成熟宿主供点选锁定。
 */
public final class AmonRoleScreenExtension extends PlayerListRoleScreenExtension<PlayerInfo> {
    public static final AmonRoleScreenExtension INSTANCE = new AmonRoleScreenExtension();

    private AmonRoleScreenExtension() {
    }

    @Override
    protected RoleScreenHelper<PlayerInfo> createHelper(LocalPlayer player) {
        RoleScreenHelper<PlayerInfo> h = new RoleScreenHelper<>(
                player,
                ModRoles.AMON,
                this::createAmonWidget,
                TEXT_PROVIDER,
                this::drawAmonSelectionHint,
                this::getAmonEligiblePlayers);
        h.setNameExtractor(info -> info.getProfile().getName());
        return h;
    }

    private AmonPlayerWidget createAmonWidget(LimitedInventoryScreen screen, int x, int y, PlayerInfo playerEntity,
            int index) {
        AmonPlayerWidget widget = new AmonPlayerWidget(screen, x, y, playerEntity);
        screen.addRoleWidget(widget);
        return widget;
    }

    private void drawAmonSelectionHint(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.amon.player_selection");
        int color = new Color(170, 0, 170).getRGB();

        int textWidth = client.font.width(text);
        context.drawString(client.font, text,
                point.x - textWidth / 2, point.y + 40, color);
    }

    private List<PlayerInfo> getAmonEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return List.of();
        }

        // 只显示已成熟、可夺舍的宿主（成熟宿主 UUID 仅同步给阿蒙本人）。
        AmonRoleData comp = RoleData.getNullable(AmonRoleData.class, client.player);
        if (comp == null) {
            return List.of();
        }
        java.util.Set<java.util.UUID> matured = comp.clientMaturedHosts;
        if (matured.isEmpty()) {
            return List.of();
        }
        return client.getConnection().getOnlinePlayers().stream()
                .filter(a -> matured.contains(a.getProfile().getId())
                        && a.getProfile().getId() != client.player.getUUID()
                        && a.getGameMode() == GameType.ADVENTURE)
                .collect(Collectors.toList());
    }
}

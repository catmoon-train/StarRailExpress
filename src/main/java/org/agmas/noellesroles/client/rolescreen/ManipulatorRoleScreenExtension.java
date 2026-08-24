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
import io.wifi.starrailexpress.api.data.RoleData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.client.widget.ManipulatorPlayerWidget;
import org.agmas.noellesroles.packet.ManipulatorControlInputC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.ManipulatorRoleData;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操纵师背包界面扩展：在背包界面显示可操控的玩家列表；操控期间打开背包会取消操控。
 */
public final class ManipulatorRoleScreenExtension extends PlayerListRoleScreenExtension<PlayerInfo> {

    public ManipulatorRoleScreenExtension() {
    }

    @Override
    protected RoleScreenHelper<PlayerInfo> createHelper(LocalPlayer player) {
        RoleScreenHelper<PlayerInfo> h = new RoleScreenHelper<>(
                player,
                ModRoles.MANIPULATOR,
                this::createManipulatorWidget,
                TEXT_PROVIDER,
                this::drawManipulatorSelectionHint,
                this::getEligiblePlayers);
        h.setNameExtractor(info -> info.getProfile().getName());
        return h;
    }

    private ManipulatorPlayerWidget createManipulatorWidget(LimitedInventoryScreen screen, int x, int y,
            PlayerInfo playerEntity, int index) {
        ManipulatorPlayerWidget widget = new ManipulatorPlayerWidget(screen, x, y, playerEntity);
        screen.addRoleWidget(widget);
        return widget;
    }

    private void drawManipulatorSelectionHint(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.manipulator.player_selection");
        int color = Color.RED.getRGB();

        int textWidth = client.font.width(text);
        context.drawString(client.font, text,
                point.x - textWidth / 2, point.y + 40, color);
    }

    private List<PlayerInfo> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return List.of();
        }

        return client.getConnection().getOnlinePlayers().stream()
                .filter(a -> isEligibleControlTarget(client, a))
                .collect(Collectors.toList());
    }

    private boolean isEligibleControlTarget(Minecraft client, PlayerInfo info) {
        if (info.getProfile().getId().equals(client.player.getUUID())
                || info.getGameMode() != GameType.ADVENTURE) {
            return false;
        }
        if (client.level == null) {
            return false;
        }
        return client.level.getPlayerByUUID(info.getProfile().getId()) instanceof AbstractClientPlayer targetPlayer
                && client.player.distanceTo(targetPlayer) <= ManipulatorRoleData.DIRECT_CONTROL_RANGE;
    }

    @Override
    public void onInventoryScreenInit(LimitedInventoryScreen screen) {
        // 操控期间打开背包即取消操控
        if (screen.player != null && ModRoles.MANIPULATOR != null) {
            ManipulatorRoleData comp = RoleData.getNullable(ManipulatorRoleData.class, screen.player);
            if (comp != null && comp.isControlling) {
                ClientPlayNetworking.send(new ManipulatorControlInputC2SPacket(0, 0f, 0f, true));
            }
        }
        super.onInventoryScreenInit(screen);
    }
}

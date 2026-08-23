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

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.RoleScreenHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.client.widget.WarlockDomainWidget;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.role_data.killer.WarlockRoleData;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 咒术师·领域展开背包界面扩展：列出"已被诅咒且存活"的候选玩家，点选即对其展开领域。
 */
public final class WarlockRoleScreenExtension extends PlayerListRoleScreenExtension<PlayerInfo> {
    public static final WarlockRoleScreenExtension INSTANCE = new WarlockRoleScreenExtension();

    private WarlockRoleScreenExtension() {
    }

    @Override
    protected RoleScreenHelper<PlayerInfo> createHelper(LocalPlayer player) {
        RoleScreenHelper<PlayerInfo> h = new RoleScreenHelper<>(
                player,
                ModRoles.WARLOCK,
                this::createWarlockWidget,
                TEXT_PROVIDER,
                this::drawWarlockHint,
                this::getEligibleVictims);
        h.setNameExtractor(info -> info.getProfile().getName());
        return h;
    }

    private WarlockDomainWidget createWarlockWidget(LimitedInventoryScreen screen, int x, int y,
            PlayerInfo playerEntity, int index) {
        WarlockDomainWidget widget = new WarlockDomainWidget(screen, x, y, playerEntity);
        screen.addRoleWidget(widget);
        return widget;
    }

    private void drawWarlockHint(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.warlock.domain_selection");
        int textWidth = client.font.width(text);
        context.drawString(client.font, text, point.x - textWidth / 2, point.y + 40, Color.RED.getRGB());
    }

    private List<PlayerInfo> getEligibleVictims() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer self = client.player;
        if (client.level == null || self == null) {
            return List.of();
        }
        WarlockRoleData comp = RoleData.getOptional(WarlockRoleData.class, self).orElse(null);
        if (comp == null) {
            return List.of();
        }
        long now = SREClient.getTicksFromGameStart();
        return client.getConnection().getOnlinePlayers().stream()
                .filter(info -> info.getGameMode() == GameType.ADVENTURE)
                .filter(info -> {
                    Long end = comp.cursedPlayers.get(info.getProfile().getId());
                    return end != null && end > now;
                })
                .collect(Collectors.toList());
    }
}

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
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.client.widget.WizardShieldWidget;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.role_data.killer.WizardRoleData;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 巫师"盔甲护身"背包界面扩展：选中该法术时，在背包显示可赋予护盾的玩家列表。
 * 列表逻辑放在 {@code LimitedInventoryScreen.init()} 末尾（TAIL）。
 */
public final class WizardRoleScreenExtension extends PlayerListRoleScreenExtension<PlayerInfo> {

    public WizardRoleScreenExtension() {
    }

    /** 背包界面 {@code init()} 末尾调用：填充选人列表并挂载搜索框。 */
    @Override
    public void onInventoryScreenInitTail(LimitedInventoryScreen screen) {
        initPlayerList(screen);
    }

    @Override
    protected RoleScreenHelper<PlayerInfo> createHelper(LocalPlayer player) {
        RoleScreenHelper<PlayerInfo> h = new RoleScreenHelper<>(
                player,
                ModRoles.WIZARD,
                this::createWizardWidget,
                TEXT_PROVIDER,
                this::drawWizardSelectionHint,
                this::getEligiblePlayers);
        h.setNameExtractor(info -> info.getProfile().getName());
        return h;
    }

    private Button createWizardWidget(LimitedInventoryScreen screen, int x, int y, PlayerInfo playerEntity,
            int index) {
        WizardShieldWidget widget = new WizardShieldWidget(screen, x, y, playerEntity);
        screen.addRoleWidget(widget);
        return widget;
    }

    private void drawWizardSelectionHint(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        WizardRoleData comp = RoleData.getOptional(WizardRoleData.class, client.player).orElse(null);
        if (comp == null) {
            return;
        }
        // 仅当选中"盔甲护身"时才显示提示
        if (comp.selectedSpell != WizardRoleData.Spell.ARMOR) {
            return;
        }
        Component text = Component.translatable("hud.wizard.player_selection");
        int textWidth = client.font.width(text);
        context.drawString(client.font, text, point.x - textWidth / 2, point.y + 40, Color.CYAN.getRGB());
    }

    private List<PlayerInfo> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return List.of();
        }
        // 仅当选中"盔甲护身"法术时显示
        WizardRoleData comp = RoleData.getOptional(WizardRoleData.class, client.player).orElse(null);
        if (comp == null) {
            return List.of();
        }
        if (comp.selectedSpell != WizardRoleData.Spell.ARMOR) {
            return List.of();
        }
        return client.getConnection().getOnlinePlayers().stream()
                .filter(a -> !a.getProfile().getId().equals(client.player.getUUID())
                        && a.getGameMode() == GameType.ADVENTURE)
                .collect(Collectors.toList());
    }
}

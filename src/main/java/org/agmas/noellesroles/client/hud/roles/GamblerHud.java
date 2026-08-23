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

package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.role_data.neutral.GamblerRoleData;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 赌徒 HUD Mixin
 * 
 * 显示赌徒的技能状态：
 * - 赌徒职业
 */
public class GamblerHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.GAMBLER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;
            // 获取赌徒组件
            var gamblerOpt = RoleData.getOptional(GamblerRoleData.class, client.player);
            if (gamblerOpt.isEmpty())
                return;
            GamblerRoleData gamblerComponent = gamblerOpt.get();

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 120; // 距离右边缘
            int y = screenHeight - 30; // 距离底部

            Font textRenderer = client.font;
            int roleDrawLeft = gamblerComponent.drawInterval - gamblerComponent.roleDrawTimer;
            if (gamblerComponent.selectedRole != null) {
                Component readyText = Component.translatable("hud.noellesroles.gambler.selected_tip",
                        RoleUtils.getRoleName(gamblerComponent.selectedRole));
                context.drawString(textRenderer, readyText, x, y - 40, CommonColors.GREEN);
                Component readyText2 = Component.translatable("hud.noellesroles.gambler.revolver_tip",
                        RoleUtils.getRoleName(gamblerComponent.selectedRole));
                context.drawString(textRenderer, readyText2, x, y - 60, CommonColors.GREEN);
            }
            if (gamblerComponent.availableRoles.size() > 0) {
                Component readyText = Component.translatable("hud.noellesroles.gambler.tip",
                        Component.keybind("key.noellesroles.ability"));
                context.drawString(textRenderer, readyText, x, y - 20, CommonColors.GREEN);
            }
            // 显示技能冷却
            int cdSeconds = (roleDrawLeft + 19) / 20;
            Component cdText = Component.translatable("hud.noellesroles.gambler.cooldown",
                    String.format("%d", cdSeconds));

            // 红色文字表示抽取间隔
            context.drawString(textRenderer, cdText, x, y, CommonColors.RED);
        });
    }
}
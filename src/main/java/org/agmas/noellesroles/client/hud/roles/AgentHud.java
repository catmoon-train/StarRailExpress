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

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.AgentRoleData;

/**
 * 探员 HUD Mixin
 * 
 * 显示探员的技能状态：
 * - 审查技能冷却时间
 * - 技能就绪提示
 */
public class AgentHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.AGENT_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            // 获取探员组件
            var detectiveOpt = RoleData.getOptional(AgentRoleData.class, client.player);
            if (detectiveOpt.isEmpty())
                return;
            AgentRoleData detectiveComponent = detectiveOpt.get();

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 120; // 距离右边缘
            int y = screenHeight - 30; // 距离底部

            Font textRenderer = client.font;

            if (detectiveComponent.cooldown > 0) {
                // 显示技能冷却
                float cdSeconds = detectiveComponent.getCooldownSeconds();
                Component cdText = Component.translatable("hud.noellesroles.detective.cooldown",
                        String.format("%.1f", cdSeconds));

                // 红色文字表示冷却中
                context.drawString(textRenderer, cdText, x, y, CommonColors.RED);

            } else {
                // 技能可用 - 显示金币消耗提示
                Component readyText = Component.translatable("hud.noellesroles.detective.ready_cost");
                context.drawString(textRenderer, readyText, x, y, CommonColors.GREEN);
            }
        });
    }
}
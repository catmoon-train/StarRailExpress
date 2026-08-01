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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.innocence.boxer.BoxerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 斗士 HUD Mixin
 * 
 * 显示斗士的技能状态：
 * - 技能冷却时间
 * - 无敌状态激活提示
 * - 技能就绪提示
 */
public class BoxerHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.FIGHTER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            // 获取斗士组件
            BoxerPlayerComponent boxerComponent = BoxerPlayerComponent.KEY.get(client.player);

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 150; // 距离右边缘
            int y = screenHeight - 30; // 距离底部

            Font textRenderer = client.font;

            // 检查无敌状态
            if (boxerComponent.isInvulnerable) {
                // 无敌激活 - 显示黄色闪烁文字
                Component activeText = Component.translatable("hud.noellesroles.boxer.active",
                        String.format("%.1f", boxerComponent.getInvulnerabilitySeconds()));

                // 使用黄色表示无敌激活
                int color = 0xFFFF00; // 黄色
                context.drawString(textRenderer, activeText, x, y, color);

            } else if (boxerComponent.cooldown > 0) {
                // 显示技能冷却
                float cdSeconds = boxerComponent.getCooldownSeconds();
                Component cdText = Component.translatable("hud.noellesroles.boxer.cooldown",
                        String.format("%.1f", cdSeconds));

                // 红色文字表示冷却中
                context.drawString(textRenderer, cdText, x, y, CommonColors.RED);

            } else {
                // 技能就绪 - 显示绿色提示
                Component readyText = Component.translatable("hud.noellesroles.boxer.ready");
                context.drawString(textRenderer, readyText, x, y, CommonColors.GREEN);
            }
        });
    }
}
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

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public abstract class ExecutionerHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.EXECUTIONER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            final Font renderer = client.font;
            final LocalPlayer player = client.player;
            ExecutionerPlayerComponent component = ExecutionerPlayerComponent.KEY.get(player);

            context.pose().pushPose();

            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            int yOffset = screenHeight - 28; // 右下角
            int xOffset = screenWidth - 180; // 距离右边缘
            if (component.targetSelected && component.target != null) {
                // 已绑定目标 - 显示保护目标
                var info = client.player.connection.getPlayerInfo(component.target);
                if (info != null) {
                    // 显示目标头像
                    context.drawPlayerFace(
                            info.getSkin().texture(),
                            xOffset, yOffset, 12);

                    Component targetText = Component.translatable("hud.executioner.target",
                            info.getProfile().getName()).withStyle(ChatFormatting.GOLD);
                    context.drawString(renderer, targetText, xOffset + 16, yOffset + 2, 0xFFAA00);
                }
            } else {
                // 等待绑定目标
                Component waitingText = Component.translatable("hud.executioner.no_target")
                        .withStyle(ChatFormatting.GRAY);
                context.drawString(renderer, waitingText, xOffset, yOffset, CommonColors.GRAY);
            }

            context.pose().popPose();
        });
    }
}

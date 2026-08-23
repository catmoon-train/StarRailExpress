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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role_data.innocence.AvengerRoleData;
import org.agmas.noellesroles.role.ModRoles;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

/**
 * 复仇者 HUD Mixin
 * 在屏幕上显示绑定目标和激活状态
 */
public class AvengerHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.AVENGER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            final Font renderer = client.font;
            final LocalPlayer player = client.player;
            var avengerData = RoleData.getOptional(AvengerRoleData.class, player);
            if (avengerData.isEmpty()) return;
            var avengerComponent = avengerData.get();

            context.pose().pushPose();

            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            int yOffset = screenHeight - 28; // 右下角
            int xOffset = screenWidth - 180; // 距离右边缘
            var refugeeC = RefugeeComponent.KEY.get(player.level());
            boolean isRefugeeAlive = false;
            if (refugeeC.isAnyRevivals) {
                isRefugeeAlive = true;
            }
            if (isRefugeeAlive) {
                Component waitingText = Component.translatable("tip.noellesroles.avenger.refugee_mode")
                        .withStyle(ChatFormatting.GRAY);
                context.drawString(renderer, waitingText, screenWidth - 18 - renderer.width(waitingText), yOffset,
                        CommonColors.GRAY);
            } else if (avengerComponent.activated) {
                // 复仇已激活 - 显示凶手信息
                Component statusText = Component.translatable("tip.noellesroles.avenger.activated",
                        avengerComponent.getKillerName().isEmpty() ? "???" : avengerComponent.getKillerName())
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                context.drawString(renderer, statusText, xOffset, yOffset, CommonColors.RED);

                // 如果知道凶手，显示凶手头像
                final var playerInfo = client.player.connection.getPlayerInfo(avengerComponent.killerUuid);
                if (avengerComponent.killerUuid != null &&
                        playerInfo != null) {
                    context.drawPlayerFace(playerInfo.getSkin().texture(),
                            xOffset, yOffset - 14, 12);

                    Component killerName = Component.literal(avengerComponent.getKillerName())
                            .withStyle(ChatFormatting.RED);
                    context.drawString(renderer, killerName, xOffset + 16, yOffset - 12, CommonColors.RED);
                }
            } else if (avengerComponent.bound && avengerComponent.targetPlayer != null) {
                // 已绑定目标 - 显示保护目标
                var target = client.player.connection.getPlayerInfo(avengerComponent.targetPlayer);
                if (target != null) {
                    // 显示目标头像
                    context.drawPlayerFace(target.getSkin().texture(),
                            xOffset, yOffset, 12);

                    Component targetText = Component.translatable("tip.noellesroles.avenger.target",
                            avengerComponent.targetName).withStyle(ChatFormatting.GOLD);
                    context.drawString(renderer, targetText, xOffset + 16, yOffset + 2, 0xFFAA00);
                }
            } else {
                // 等待绑定目标
                Component waitingText = Component.translatable("tip.noellesroles.avenger.waiting")
                        .withStyle(ChatFormatting.GRAY);
                context.drawString(renderer, waitingText, xOffset, yOffset, CommonColors.GRAY);
            }

            context.pose().popPose();
        });
    }
}
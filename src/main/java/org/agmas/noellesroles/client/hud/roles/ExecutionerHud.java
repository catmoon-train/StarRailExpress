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

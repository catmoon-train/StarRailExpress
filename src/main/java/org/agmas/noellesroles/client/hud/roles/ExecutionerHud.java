package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.executioner.ExecutionerPlayerComponent;

public abstract class ExecutionerHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.EXECUTIONER_ID, (context, tickCounter) -> {
            final Minecraft client = Minecraft.getInstance();
            final LocalPlayer player = client.player;
            final Font renderer = client.font;
            if (SREClient.isPlayerSpectator())
                return;

            ExecutionerPlayerComponent executionerPlayerComponent = ExecutionerPlayerComponent.KEY.get(player);

            // 检查是否有选定的目标
            if (executionerPlayerComponent.target != null) {
                var playerListEntry = Minecraft.getInstance().player.connection
                        .getPlayerInfo(executionerPlayerComponent.target);
                if (playerListEntry == null)
                    return;

                context.pose().pushPose();
                context.pose().translate((float) context.guiWidth() / 2.0F,
                        (float) context.guiHeight() / 2.0F + 6.0F, 0.0F);
                context.pose().scale(0.6F, 0.6F, 1.0F);

                // 显示目标玩家名称
                Component name = Component.translatable("hud.executioner.target", playerListEntry.getProfile());
                context.drawString(renderer, name, -renderer.width(name) / 2, 32, CommonColors.RED);

                // 如果目标已经死亡，显示等待状态
                if (executionerPlayerComponent.won) {
                    Component status = Component.translatable("hud.executioner.target_died");
                    context.drawString(renderer, status, -renderer.width(status) / 2, 44, CommonColors.YELLOW);
                }

                context.pose().popPose();
            } else if (!executionerPlayerComponent.targetSelected) {
                // 显示需要选择目标的提示
                context.pose().pushPose();
                context.pose().translate((float) context.guiWidth() / 2.0F,
                        (float) context.guiHeight() / 2.0F + 6.0F, 0.0F);
                context.pose().scale(0.6F, 0.6F, 1.0F);

                Component prompt = Component.translatable("hud.executioner.no_target");
                context.drawString(renderer, prompt, -renderer.width(prompt) / 2, 32, CommonColors.YELLOW);

                context.pose().popPose();
            }
        });
    }
}

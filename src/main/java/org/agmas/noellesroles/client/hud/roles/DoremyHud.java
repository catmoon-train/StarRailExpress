package org.agmas.noellesroles.client.hud.roles;

import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.role_data.killer.DoremyRoleData;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public class DoremyHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(THMiscRoles.DOREMY.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            // 获取探员组件
            var comp = RoleData.getOptional(DoremyRoleData.class, client.player);
            if (comp.isEmpty())
                return;
            final var roledata = comp.get();

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10; // 距离右边缘
            int y = screenHeight - 20; // 距离底部

            Font textRenderer = client.font;

            if (roledata.cooldownForDoremyDream > 0) {

                Component cdText = Component
                        .translatable("hud.noellesroles.doremy_dream.cooldown", roledata.cooldownForDoremyDream / 20)
                        .withStyle(ChatFormatting.AQUA);
                context.drawString(textRenderer, cdText, x - textRenderer.width(cdText), y, 0xffffffff);

            } else {
                Component cdText = Component.translatable("hud.noellesroles.doremy_dream.ready")
                        .withStyle(ChatFormatting.AQUA);
                context.drawString(textRenderer, cdText, x - textRenderer.width(cdText), y, 0xffffffff);
            }
            if (roledata.cooldownForDoremyGhost > 0) {

                Component cdText = Component
                        .translatable("hud.noellesroles.doremy_ghost.cooldown", roledata.cooldownForDoremyGhost / 20)
                        .withStyle(ChatFormatting.AQUA);
                context.drawString(textRenderer, cdText, x - textRenderer.width(cdText), y - 10, 0xffffffff);

            } else {
                Component cdText = Component.translatable("hud.noellesroles.doremy_ghost.ready")
                        .withStyle(ChatFormatting.AQUA);
                context.drawString(textRenderer, cdText, x - textRenderer.width(cdText), y - 10, 0xffffffff);
            }
        });
    }
}

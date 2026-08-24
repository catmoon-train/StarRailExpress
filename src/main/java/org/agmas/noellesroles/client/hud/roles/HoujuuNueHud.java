package org.agmas.noellesroles.client.hud.roles;

import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.role_data.killer.HoujuuNueRoleData;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public class HoujuuNueHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(THMiscRoles.HOUJUU_NUE.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            // 获取探员组件
            var comp = RoleData.getOptional(HoujuuNueRoleData.class, client.player);
            if (comp.isEmpty())
                return;
            final var roledata = comp.get();

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10; // 距离右边缘
            int y = screenHeight - 20; // 距离底部

            Font textRenderer = client.font;

            if (roledata.tickCounter > 0) {
                int cdSeconds = roledata.tickCounter / 20;
                Component cdText = Component.translatable("hud.houjuu_nue.tip.2", cdSeconds)
                        .withStyle(ChatFormatting.AQUA);
                context.drawString(textRenderer, cdText, x - textRenderer.width(cdText), y, 0xffffffff);

            }
            if (roledata.slownessLayers > 0) {
                Component cdText = Component.translatable("hud.houjuu_nue.tip.1", roledata.slownessLayers)
                        .withStyle(ChatFormatting.RED);
                context.drawString(textRenderer, cdText, x - textRenderer.width(cdText), y - 10, 0xffffffff);
            }
        });
    }
}

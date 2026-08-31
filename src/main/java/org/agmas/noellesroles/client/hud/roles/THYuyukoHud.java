package org.agmas.noellesroles.client.hud.roles;

import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.role_data.neutral.THYuyukoRoleData;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public class THYuyukoHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(THMiscRoles.YUYUKO.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            var comp = RoleData.getOptional(THYuyukoRoleData.class, client.player);
            if (comp.isEmpty())
                return;
            final var roledata = comp.get();

            // 渲染位置 - left下角
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = 10;
            int y = screenHeight - 20; // 距离底部

            Font textRenderer = client.font;

            if (roledata.winnerNeedCount > 0) {
                Component cdText = Component
                        .translatable("hud.noellesroles.yuyuko.target", roledata.ateCount, roledata.winnerNeedCount)
                        .withStyle(ChatFormatting.AQUA);
                context.drawString(textRenderer, cdText, x, y, 0xffffffff);

            }
        });
    }
}

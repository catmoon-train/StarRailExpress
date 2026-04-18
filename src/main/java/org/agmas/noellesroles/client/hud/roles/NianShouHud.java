package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.nian_shou.NianShouPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class NianShouHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.NIAN_SHOU_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            // 获取红包组件
            var nianShouComponent = NianShouPlayerComponent.KEY.get(client.player);

            if (nianShouComponent == null)
                return;

            // 渲染红包数量
            int redPacketCount = nianShouComponent.getRedPacketCount();

            var font = client.font;
            int x = guiGraphics.guiWidth() - 10;
            int y = guiGraphics.guiHeight() - 30;

            MutableComponent text = net.minecraft.network.chat.Component
                    .translatable("hud.noellesroles.nianshou.red_packets", redPacketCount);

            guiGraphics.drawString(font, text, x - font.width(text), y, 0xFFD700, true);
        });
    }
}

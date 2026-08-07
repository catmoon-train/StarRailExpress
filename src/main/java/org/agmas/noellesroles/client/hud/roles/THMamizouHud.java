package org.agmas.noellesroles.client.hud.roles;

import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.touhou.THMiscRoles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.util.SREClientUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class THMamizouHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(THMiscRoles.MAMIZOU_ID, (context, deltaTracker) -> {
            var client = Minecraft.getInstance();
            var cca = SREAbilityPlayerComponent.KEY.get(client.player);
            final var font = client.font;

            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = 10;
            int y = screenHeight - 20;
            if (cca.targetUUID != null) {
                String name = SREClientUtils.getPlayerNameByUid(cca.targetUUID);
                if (name == null)
                    return;
                final var killsText = Component.translatable("hud.noellesroles.mamizou_select", name)
                        .withStyle(ChatFormatting.AQUA);
                context.drawString(font, killsText, x, y, 0xffffffff);
            }

        });
    }
}

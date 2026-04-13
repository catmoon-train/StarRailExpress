package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.WatcherPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.*;

public class SuperLooseEndHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.SUPER_LOOSE_END_ID, (guiGraphics, deltaTracker) -> {
            // 渲染watcher的提示
            var client = Minecraft.getInstance();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight;
            var abpc = SREArmorPlayerComponent.KEY.get(client.player);
            {
                var text = Component
                        .translatable("hud.bartender.has_armor",
                                abpc.armor)
                        .withStyle(ChatFormatting.GOLD);
                // 右下角渲染护盾数量文本
                guiGraphics.drawString(font, text, 10, yOffset - font.lineHeight - 4,
                        Color.WHITE.getRGB());
            }
        });

    }
}

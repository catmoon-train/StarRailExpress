package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;

public final class VeteranHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.VETERAN_ID, (context, tickCounter) -> {
            var client = Minecraft.getInstance();
            if (client.player == null) return;
            var ability = SREAbilityPlayerComponent.KEY.get(client.player);
            int x = context.guiWidth() - 10;
            int y = context.guiHeight() - 10 - client.font.lineHeight;
            var font = client.font;
            if (ability.cooldown > 0) {
                double seconds = ability.cooldown / 20.0;
                var text = Component.translatable("hud.veteran.cooldown", String.format("%.1f", seconds))
                        .withStyle(ChatFormatting.RED);
                context.drawString(font, text, x - font.width(text), y, 0xFF5555);
            } else {
                var text = Component.translatable("hud.veteran.ready")
                        .withStyle(ChatFormatting.GREEN);
                context.drawString(font, text, x - font.width(text), y, 0x55FF55);
            }
        });
    }
}

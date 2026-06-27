package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;

public final class CakeMakerHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.CAKE_MAKER_ID, (context, tickCounter) -> {
            var client = Minecraft.getInstance();
            if (client.player == null) return;
            var comp = ModComponents.CAKE_MAKER.get(client.player);
            int x = context.guiWidth() - 10;
            int y = context.guiHeight() - 10 - client.font.lineHeight;
            var font = client.font;
            if (comp.cooldown > 0) {
                int seconds = (comp.cooldown + 19) / 20;
                var text = Component.translatable("hud.cake_maker.cooldown", seconds)
                        .withStyle(ChatFormatting.RED);
                context.drawString(font, text, x - font.width(text), y, 0xFF5555);
            } else {
                var text = Component.translatable("hud.cake_maker.ready")
                        .withStyle(ChatFormatting.GREEN);
                context.drawString(font, text, x - font.width(text), y, 0x55FF55);
            }
        });
    }
}

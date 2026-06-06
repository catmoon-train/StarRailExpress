package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.embalmer.EmbalmerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class EmbalmerHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.EMBALMER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator()) return;
            var comp = EmbalmerPlayerComponent.KEY.get(client.player);
            Font font = client.font;
            int sw = client.getWindow().getGuiScaledWidth();
            int sy = client.getWindow().getGuiScaledHeight();

            Component text;
            if (comp.masqueradeTicksLeft > 0) {
                int sec = (comp.masqueradeTicksLeft + 19) / 20;
                text = Component.translatable("hud.noellesroles.embalmer.active", sec).withStyle(ChatFormatting.LIGHT_PURPLE);
            } else if (comp.masqueradeCooldown > 0) {
                int sec = (comp.masqueradeCooldown + 19) / 20;
                text = Component.translatable("hud.noellesroles.embalmer.cooldown", sec).withStyle(ChatFormatting.GRAY);
            } else {
                text = Component.translatable("hud.noellesroles.embalmer.ready").withStyle(ChatFormatting.GREEN);
            }
            context.drawString(font, text, sw - font.width(text) - 8, sy - 24, 0xFFFFFF);
        });
    }
}

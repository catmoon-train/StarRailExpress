package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
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
            if (comp.masqueradeCooldown <= 0 && comp.masqueradeTicksLeft <= 0) return;
            Font font = client.font;
            int sw = client.getWindow().getGuiScaledWidth();
            int y = client.getWindow().getGuiScaledHeight() - 80;
            if (comp.masqueradeCooldown > 0) {
                int sec = (comp.masqueradeCooldown + 19) / 20;
                Component t = Component.literal("Masquerade: " + sec + "s").withColor(0x8844CC);
                context.drawString(font, t, (sw - font.width(t)) / 2, y, 0x8844CC);
            } else if (comp.masqueradeTicksLeft > 0) {
                int sec = (comp.masqueradeTicksLeft + 19) / 20;
                Component t = Component.literal("Masquerade: " + sec + "s").withColor(0x44CC44);
                context.drawString(font, t, (sw - font.width(t)) / 2, y, 0x44CC44);
            }
        });
    }
}

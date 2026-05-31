package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.warlock.WarlockPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class WarlockHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.WARLOCK_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator()) return;
            var comp = WarlockPlayerComponent.KEY.get(client.player);
            if (comp.markCooldown <= 0 && comp.killCooldown <= 0) return;

            Font font = client.font;
            int sw = client.getWindow().getGuiScaledWidth();
            int y = client.getWindow().getGuiScaledHeight() - 80;

            if (comp.markCooldown > 0) {
                int sec = (comp.markCooldown + 19) / 20;
                Component t = Component.literal("Mark: " + sec + "s").withColor(0xFFAA00);
                context.drawString(font, t, (sw - font.width(t)) / 2, y - 12, 0xFFAA00);
            }
            if (comp.killCooldown > 0) {
                int sec = (comp.killCooldown + 19) / 20;
                Component t = Component.literal("Hex: " + sec + "s").withColor(0xFF4444);
                context.drawString(font, t, (sw - font.width(t)) / 2, y, 0xFF4444);
            }
        });
    }
}

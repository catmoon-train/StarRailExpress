package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.skincrawler.SkincrawlerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class SkincrawlerHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.SKINCRAWLER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator()) return;
            var comp = SkincrawlerPlayerComponent.KEY.get(client.player);
            if (comp.stealCooldown <= 0) return;
            Font font = client.font;
            int sw = client.getWindow().getGuiScaledWidth();
            int y = client.getWindow().getGuiScaledHeight() - 80;
            int sec = (comp.stealCooldown + 19) / 20;
            Component t = Component.literal("Steal: " + sec + "s").withColor(0xCC4444);
            context.drawString(font, t, (sw - font.width(t)) / 2, y, 0xCC4444);
        });
    }
}

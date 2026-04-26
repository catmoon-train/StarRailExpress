package io.wifi.events.day_night_fight.client;

import io.wifi.events.day_night_fight.DNFPlayerComponent;
import io.wifi.events.day_night_fight.DNFRoles;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;

@Environment(EnvType.CLIENT)
public class DNFHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(DNFRoles.KILLER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            DNFPlayerComponent component = DNFPlayerComponent.KEY.get(client.player);
            Font font = client.font;
            int x = 8;
            int y = client.getWindow().getGuiScaledHeight() - 42;
            Component blood = Component.translatable("hud.dnf.blood", component.getBlood());
            Component bodies = Component.translatable("hud.dnf.bodies", component.getBodiesEaten());
            context.drawString(font, blood, x, y, 0xFFFF5555, true);
            context.drawString(font, bodies, x, y + 10, 0xFFAA0000, true);
        });
    }
}

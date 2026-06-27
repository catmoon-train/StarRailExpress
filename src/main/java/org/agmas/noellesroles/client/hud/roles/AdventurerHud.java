package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocence.adventurer.AdventurerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public final class AdventurerHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.ADVENTURER_ID, (context, tickCounter) -> {
            if (SREClient.isPlayerSpectator()) return;
            var player = Minecraft.getInstance().player;
            AdventurerPlayerComponent adv = ModComponents.ADVENTURER.get(player);

            int x = context.guiWidth() - 180;
            int y = context.guiHeight() - 40;
            var font = Minecraft.getInstance().font;

            context.drawString(font,
                    Component.translatable("hud.noellesroles.adventurer.immunities"),
                    x, y, 0x55FF55);

            if (adv.waypointCooldown > 0) {
                int sec = (adv.waypointCooldown + 19) / 20;
                context.drawString(font,
                        Component.translatable("hud.noellesroles.adventurer.waypoint_cd", sec),
                        x, y + 11, 0xAAAAAA);
            } else {
                context.drawString(font,
                        Component.translatable("hud.noellesroles.adventurer.waypoint_ready"),
                        x, y + 11, 0xFFD700);
            }
        });
    }
}

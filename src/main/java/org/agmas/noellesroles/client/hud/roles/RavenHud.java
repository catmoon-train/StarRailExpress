package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.neutral.raven.RavenPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public final class RavenHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.RAVEN_ID, (context, tickCounter) -> {
            if (SREClient.isPlayerSpectator()) return;
            var player = Minecraft.getInstance().player;
            RavenPlayerComponent raven = ModComponents.RAVEN.get(player);
            int x = context.guiWidth() - 180;
            int y = context.guiHeight() - 40;

            // Hunt charges
            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.raven.charges", raven.charges, RavenPlayerComponent.MAX_CHARGES),
                    x, y, 0x6B4B9E);

            // Kill progress
            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.raven.kills", raven.kills, raven.requiredKills),
                    x, y + 11, 0xA66DCC);

            // Cooldown countdown
            if (raven.cooldownTicks > 0) {
                int seconds = (raven.cooldownTicks + 19) / 20;
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.raven.cooldown", seconds),
                        x, y + 22, 0xAAAAAA);
            }

            // Hunt time remaining during hunt
            if (raven.isHunting()) {
                int seconds = (raven.huntTicks + 19) / 20;
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.raven.hunt_time", seconds),
                        x, y - 11, 0xCC8844);
            }

            // Target role during hunt
            if (raven.isHunting() && raven.targetRoleId != null) {
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.raven.target",
                                Component.translatable("announcement.star.role." + raven.targetRoleId.getPath())),
                        x, y - 22, 0xFF5555);
            }
        });
    }
}

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
            context.drawString(Minecraft.getInstance().font, Component.translatable("hud.noellesroles.raven.charges", raven.charges, RavenPlayerComponent.MAX_CHARGES), x, y, 0x6B4B9E);
            context.drawString(Minecraft.getInstance().font, Component.translatable("hud.noellesroles.raven.kills", raven.kills, raven.requiredKills), x, y + 11, 0xA66DCC);
            if (raven.isHunting() && raven.targetRoleId != null) {
                context.drawString(Minecraft.getInstance().font, Component.translatable("hud.noellesroles.raven.target", Component.translatable("announcement.star.role." + raven.targetRoleId.getPath())), x, y - 11, 0xFF5555);
            }
        });
    }
}

package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.vulture.VulturePlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class VultureHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.VULTURE_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;

            SREAbilityPlayerComponent abilityPlayerComponent = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY
                    .get(client.player);
            VulturePlayerComponent vulturePlayerComponent = VulturePlayerComponent.KEY.get(client.player);
            int drawY = context.guiHeight();

            Component line = Component.translatable("tip.vulture", vulturePlayerComponent.bodiesEaten,
                    vulturePlayerComponent.bodiesRequired);

            if (abilityPlayerComponent.cooldown > 0) {
                line = Component.translatable("tip.noellesroles.cooldown", abilityPlayerComponent.cooldown / 20);
            }

            drawY -= client.font.wordWrapHeight(line, 999999);
            context.drawString(client.font, line, context.guiWidth() - client.font.width(line), drawY,
                    ModRoles.VULTURE.color());
        });
    }
}

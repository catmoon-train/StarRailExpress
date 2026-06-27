package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.innocence.recaller.RecallerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class RecallerHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.RECALLER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;

            SREAbilityPlayerComponent abilityPlayerComponent = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY
                    .get(client.player);
            RecallerPlayerComponent recallerPlayerComponent = RecallerPlayerComponent.KEY.get(client.player);
            SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(client.player);

            int drawY = context.guiHeight();

            Component line = Component.translatable("tip.recaller.teleport",
                    NoellesrolesClient.abilityBind.getTranslatedKeyMessage());
            if (!recallerPlayerComponent.placed) {
                line = Component.translatable("tip.recaller.place",
                        NoellesrolesClient.abilityBind.getTranslatedKeyMessage());
            } else {
                if (playerShopComponent.balance < 100) {
                    line = Component.translatable("tip.recaller.not_enough_money");
                }
            }

            if (abilityPlayerComponent.cooldown > 0) {
                line = Component.translatable("tip.noellesroles.cooldown",
                        abilityPlayerComponent.cooldown / 20);
            }

            drawY -= client.font.wordWrapHeight(line, 999999);
            context.drawString(client.font, line,
                    context.guiWidth() - client.font.width(line), drawY, ModRoles.RECALLER.color());
        });
    }
}

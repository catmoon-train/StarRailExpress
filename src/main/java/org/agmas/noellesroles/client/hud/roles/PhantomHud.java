package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;

public class PhantomHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.PHANTOM_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;
            if (client.player.hasEffect(ModEffects.SKILL_BANED))
                return;
            SREAbilityPlayerComponent abilityPlayerComponent = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY
                    .get(client.player);

            int drawY = context.guiHeight();

            Component line = Component.translatable("tip.phantom",
                    NoellesrolesClient.abilityBind.getTranslatedKeyMessage());

            if (abilityPlayerComponent.cooldown > 0) {
                line = Component.translatable("tip.noellesroles.cooldown", abilityPlayerComponent.cooldown / 20);
            }
            var inve = client.player.getEffect(MobEffects.INVISIBILITY);
            if (inve != null) {
                int time = inve.getDuration();
                if (time > 0) {
                    line = Component.translatable("tip.phantom.activing", time / 20,
                            Component.keybind("key.noellesroles.ability"));
                }
            }

            drawY -= client.font.lineHeight;
            context.drawString(client.font, line, context.guiWidth() - client.font.width(line) - 12,
                    drawY - 12, CommonColors.RED);
        });
    }
}

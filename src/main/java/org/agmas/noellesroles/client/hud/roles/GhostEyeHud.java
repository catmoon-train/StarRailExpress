package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 鬼眼·杨间 HUD：显示主动技能「诡域」的客户端冷却倒计时。
 */
public class GhostEyeHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.GHOST_EYE_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;

            final var abilityComp = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY.get(client.player);
            MutableComponent line;
            if (abilityComp.cooldown > 0) {
                line = Component.translatable("hud.ghost_eye.cooldown", abilityComp.cooldown / 20);
            } else {
                line = Component.translatable("hud.ghost_eye.ready");
            }
            context.drawString(client.font, line,
                    context.guiWidth() - client.font.width(line) - 12,
                    context.guiHeight() - 20, ModRoles.GHOST_EYE.color());
        });
    }
}

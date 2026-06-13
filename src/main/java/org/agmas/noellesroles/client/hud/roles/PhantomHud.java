package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;

/**
 * Phantom-specific HUD overlay.
 *
 * Since the unified skill HUD ({@link io.wifi.starrailexpress.api.RoleSkill}) already
 * renders the skill card (name + cooldown/ready state), this overlay only adds the
 * Phantom-specific detail: the remaining invisibility duration when the effect is active,
 * plus a hint about the toggle key.
 */
public class PhantomHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.PHANTOM_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;
            if (client.player.hasEffect(ModEffects.SKILL_BANED))
                return;

            // Only render the extra invisibility status line — the unified HUD
            // already shows the skill name + cooldown / ready state.
            var invisibility = client.player.getEffect(MobEffects.INVISIBILITY);
            if (invisibility == null || invisibility.getDuration() <= 0) {
                return;
            }

            Component line = Component.translatable("tip.phantom.activing", invisibility.getDuration() / 20,
                    Component.keybind("key.noellesroles.ability"));

            int drawY = context.guiHeight() - client.font.lineHeight - 12;
            context.drawString(client.font, line, context.guiWidth() - client.font.width(line) - 12,
                    drawY, 0xFFAA4444);
        });
    }
}

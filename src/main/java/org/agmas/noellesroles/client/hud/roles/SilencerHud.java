package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.silencer.SilencerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class SilencerHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.SILENCER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;

            final var comp = SilencerPlayerComponent.KEY.get(client.player);

            MutableComponent content;
            if (comp.phase > 0) {
                // Skill active - show current phase
                content = Component.translatable("hud.silencer.active",
                        Component.translatable("hud.silencer.phase" + comp.phase),
                        comp.phaseTimer / 20);
            } else if (comp.skillCooldownTicks < 0) {
                content = Component.translatable("hud.silencer.cooldown",
                        -comp.skillCooldownTicks / 20);
            } else {
                content = Component.translatable("hud.silencer.ready");
            }
            context.drawString(client.font, content,
                    context.guiWidth() - client.font.width(content) - 12,
                    context.guiHeight() - 20, ModRoles.SILENCER.color());
        });
    }
}

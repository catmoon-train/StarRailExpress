package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.morphling.MorphlingPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class MorphlingHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.MORPHLING_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;

            final var morphComp = MorphlingPlayerComponent.KEY.get(client.player);

            final var morphTicks = morphComp.getMorphTicks();
            int seconds = (int) (morphTicks * 0.05);
            boolean is_cooldown = false;
            if (seconds < 0) {
                seconds = -seconds;
                is_cooldown = true;
            }
            MutableComponent content;
            if (seconds > 0) {
                if (is_cooldown) {
                    content = Component.translatable("morphling.cooldown", (seconds));
                } else {
                    content = Component.translatable("morphling.tip", (seconds));
                }
            } else
                content = Component.translatable("morphling.ready");
            context.drawString(client.font, content,
                    context.guiWidth() - client.font.width(content) - 12,
                    context.guiHeight() - 20, ModRoles.MORPHLING.color());

            // 举刀假人技能冷却（与变形冷却分开，存在 SREAbilityPlayerComponent.cooldown 中）
            final var abilityComp = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY.get(client.player);
            MutableComponent dummyLine;
            if (abilityComp.cooldown > 0) {
                dummyLine = Component.translatable("morphling.dummy.cooldown", abilityComp.cooldown / 20);
            } else {
                dummyLine = Component.translatable("morphling.dummy.ready");
            }
            context.drawString(client.font, dummyLine,
                    context.guiWidth() - client.font.width(dummyLine) - 12,
                    context.guiHeight() - 32, ModRoles.MORPHLING.color());
        });
    }
}

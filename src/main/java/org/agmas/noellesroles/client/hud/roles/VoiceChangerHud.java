package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.voice_changer.VoiceChangerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class VoiceChangerHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.VOICE_CHANGER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            VoiceChangerPlayerComponent vc = VoiceChangerPlayerComponent.KEY.get(client.player);
            MobEffect effect = VoiceChangerPlayerComponent.VOICE_EFFECTS.get(vc.currentVoiceType).value();
            int level = vc.currentVoiceLevel + 1; // 0 级 = 实际 1 级
            Component line = Component.translatable("tip.voice_changer",
                    Component.translatable(effect.getDescriptionId()), level);
            int drawY = context.guiHeight() - client.font.wordWrapHeight(line, 999999) - 8;
            context.drawString(client.font, line, context.guiWidth() - client.font.width(line), drawY,
                    0xADD8E6); // 淡蓝色
        });
    }
}

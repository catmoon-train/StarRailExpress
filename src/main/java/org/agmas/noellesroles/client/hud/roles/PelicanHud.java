package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class PelicanHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.PELICAN_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator())
                return;

            PelicanPlayerComponent comp = PelicanPlayerComponent.KEY.get(client.player);
            if (comp == null) return;

            int guiWidth = context.guiWidth();
            int guiHeight = context.guiHeight();

            // 右下角：吞噬进度 (已吞噬数/目标数)
            Component progressLine = Component.translatable("hud.noellesroles.pelican.progress",
                    comp.eatenCount, comp.requiredEaten);
            int progressX = guiWidth - client.font.width(progressLine) - 10;
            int progressY = guiHeight - 30;
            context.drawString(client.font, progressLine, progressX, progressY, ModRoles.PELICAN.color(), true);

            // 冷却信息（在进度上方显示）
            if (comp.cooldownTicks > 0) {
                Component cooldownLine = Component.translatable("tip.noellesroles.cooldown",
                        comp.cooldownTicks / 20);
                int cooldownX = guiWidth - client.font.width(cooldownLine) - 10;
                int cooldownY = progressY - client.font.lineHeight - 2;
                context.drawString(client.font, cooldownLine, cooldownX, cooldownY, 0xFF5555, true);
            }
        });
    }
}

package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocent.mortician.MorticianPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class MorticianHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.MORTICIAN_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.gameComponent == null) {
                return;
            }
            if (!SREClient.isPlayerAliveAndInSurvival()) {
                return;
            }

            MorticianPlayerComponent component = ModComponents.MORTICIAN.get(client.player);
            if (component == null) {
                return;
            }

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            
            // 在金币下方显示冷却时间
            int x = screenWidth - 120;
            int y = screenHeight - 25;

            if (component.isCooldownReady()) {
                Component readyText = Component.translatable("hud.noellesroles.mortician.ready")
                        .withStyle(ChatFormatting.GREEN);
                guiGraphics.drawString(client.font, readyText, x, y, 0xFFFFFF);
            } else {
                Component cooldownText = Component.translatable("hud.noellesroles.mortician.cooldown", 
                        component.getRemainingCooldown())
                        .withStyle(ChatFormatting.RED);
                guiGraphics.drawString(client.font, cooldownText, x, y, 0xAAAAAA);
            }
        });
    }
}

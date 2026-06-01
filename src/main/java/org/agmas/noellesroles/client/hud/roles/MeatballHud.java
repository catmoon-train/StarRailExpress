package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocent.meatball.MeatballPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class MeatballHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.MEATBALL_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.gameComponent == null) {
                return;
            }
            if (!SREClient.isPlayerAliveAndInSurvival()) {
                return;
            }

            MeatballPlayerComponent component = ModComponents.MEATBALL.get(client.player);
            if (component == null) {
                return;
            }

            int bounty = component.getBounty();
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            
            // 在金币下方显示赏金
            int x = screenWidth - 120;
            int y = screenHeight - 25;

            if (bounty > 0) {
                Component bountyText = Component.translatable("hud.noellesroles.meatball.bounty", bounty)
                        .withStyle(ChatFormatting.GOLD);
                guiGraphics.drawString(client.font, bountyText, x, y, 0xFFFFFF);
            } else {
                Component noBountyText = Component.translatable("hud.noellesroles.meatball.no_bounty")
                        .withStyle(ChatFormatting.GRAY);
                guiGraphics.drawString(client.font, noBountyText, x, y, 0xAAAAAA);
            }
        });
    }
}

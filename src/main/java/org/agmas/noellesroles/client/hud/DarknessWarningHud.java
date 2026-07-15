package org.agmas.noellesroles.client.hud;

import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.SREClientWarningTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * BannedBlockWarrningHud
 */
public class DarknessWarningHud {

    public static void register() {
        CommonHudRenderCallback.EVENT.register((ctx, delta) -> {
            final var role = SREClient.getCachedPlayerRole();
            final var client = Minecraft.getInstance();
            if (role == null || client.player == null || client.level == null)
                return;
            if (SREClient.areaComponent == null)
                return;
            if (role.isKillerTeam()) {
                return;
            }
            if (SREClientWarningTickEvents.darknessTime <= 0)
                return;
            int limit = SREClient.areaComponent.areasSettings.deadInDarknessTime;
            if (limit <= 0)
                return;
            int leftTime = limit - SREClientWarningTickEvents.darknessTime;

            if (leftTime <= -100) {
                return;
            }
            if (leftTime < 0)
                leftTime = 0;
            {
                ctx.pose().pushPose();
                ctx.pose().translate((float) (ctx.guiWidth() / 2),
                        (float) (ctx.guiHeight() - 78 - OtherRolesRegister.warningOffset), 0.0F);
                final var text = Component.translatable("message.starrailexpress.banned_blocks.warning",
                        Component.literal("" + (int) (leftTime / 20)).withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.RED);
                ctx.drawCenteredString(client.font, text, 0, -4, 0xffffffff);
                ctx.pose().popPose();
                OtherRolesRegister.warningOffset += 12;
            }
        });
    }

}

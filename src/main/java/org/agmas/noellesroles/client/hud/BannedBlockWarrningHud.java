package org.agmas.noellesroles.client.hud;

import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.SREClientBannedBlockTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * BannedBlockWarrningHud
 */
public class BannedBlockWarrningHud {

    public static void register() {
        CommonHudRenderCallback.EVENT.register((ctx, delta) -> {
            final var role = SREClient.getCachedPlayerRole();
            final var client = Minecraft.getInstance();
            if (role == null || client.player == null || client.level == null)
                return;

            final var level = client.level;
            if (SREClientBannedBlockTickEvents.bannedBlockPlayerInfo == null ||
                    SREClientBannedBlockTickEvents.bannedBlockInfo == null)
                return;
            final long leftTime;
            if (SREGameWorldComponent.isKillerTeamRoleStatic(role)) {
                leftTime = SREClientBannedBlockTickEvents.bannedBlockInfo.deathTimeForKillers() - level.getGameTime()
                        - SREClientBannedBlockTickEvents.bannedBlockPlayerInfo.standonTick;

            } else {
                leftTime = SREClientBannedBlockTickEvents.bannedBlockInfo.deathTimeForInnocent() - level.getGameTime()
                        - SREClientBannedBlockTickEvents.bannedBlockPlayerInfo.standonTick;
            }
            
            if (leftTime <= -100) {
                return;
            }
            
            {
                ctx.pose().pushPose();
                ctx.pose().translate((float) (ctx.guiWidth() / 2),
                        (float) (ctx.guiHeight() - 78), 0.0F);
                final var text = Component.translatable("message.starrailexpress.banned_blocks.warning",
                        Component.literal("" + (int) (leftTime / 20)).withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.RED);
                ctx.drawCenteredString(client.font, text, 0, -4, 0xffffffff);
                ctx.pose().popPose();
            }
        });
    }

}

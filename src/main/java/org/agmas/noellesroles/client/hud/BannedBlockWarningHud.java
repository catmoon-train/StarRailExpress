/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client.hud;

import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.SREClientWarningTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * BannedBlockWarrningHud
 */
public class BannedBlockWarningHud {

    public static void register() {
        CommonHudRenderCallback.EVENT.register((ctx, delta) -> {
            final var role = SREClient.getCachedPlayerRole();
            final var client = Minecraft.getInstance();
            if (role == null || client.player == null || client.level == null)
                return;

            final var level = client.level;
            if (SREClientWarningTickEvents.bannedBlockPlayerInfo == null ||
                    SREClientWarningTickEvents.bannedBlockInfo == null)
                return;
            long leftTime;
            if (SREGameWorldComponent.isKillerTeamRoleStatic(role)) {
                leftTime = SREClientWarningTickEvents.bannedBlockInfo.deathTimeForKillers() - (level.getGameTime()
                        - SREClientWarningTickEvents.bannedBlockPlayerInfo.standonTick);

            } else {
                leftTime = SREClientWarningTickEvents.bannedBlockInfo.deathTimeForInnocent() - (level.getGameTime()
                        - SREClientWarningTickEvents.bannedBlockPlayerInfo.standonTick);
            }

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

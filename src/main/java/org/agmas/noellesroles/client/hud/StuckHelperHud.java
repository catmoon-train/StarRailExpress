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
import org.agmas.noellesroles.utils.StuckHelperUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/**
 * BannedBlockWarrningHud
 */
public class StuckHelperHud {
    public static boolean lastStuckState = false;

    public static void register() {
        CommonHudRenderCallback.EVENT.register((ctx, delta) -> {
            final var client = Minecraft.getInstance();
            if (client.player == null || client.level == null)
                return;
            if (client.player.isSpectator() || client.player.isCreative())
                return;
            if (client.level.getGameTime() % 20 == 0) {
                lastStuckState = (StuckHelperUtils.isPlayerStuck(client.player));
            }
            if (lastStuckState) {
                ctx.pose().pushPose();
                ctx.pose().translate((float) (ctx.guiWidth() / 2),
                        (float) (ctx.guiHeight() - 78 - OtherRolesHudRegister.warningOffset), 0.0F);
                Component text;
                if (client.player.getCooldowns().isOnCooldown(Items.STRUCTURE_VOID)) {
                    text = Component.translatable("message.noellesroles.commands.stuck.cooldown",
                            Component.literal("/stuck").withStyle(ChatFormatting.GREEN))
                            .withStyle(ChatFormatting.RED);
                } else {

                    text = Component.translatable("message.tip.stuck_help",
                            Component.literal("/stuck").withStyle(ChatFormatting.GREEN))
                            .withStyle(ChatFormatting.GOLD);
                }
                ctx.drawCenteredString(client.font, text, 0, -4, 0xffffffff);
                ctx.pose().popPose();
                OtherRolesHudRegister.warningOffset += 12;
            }
        });
    }

}

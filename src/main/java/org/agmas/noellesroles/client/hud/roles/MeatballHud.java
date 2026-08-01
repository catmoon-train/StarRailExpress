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

package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocence.meatball.MeatballPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class MeatballHud {

    private static final double DOOR_CHECK_RANGE = 1.5;

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

            int bountyY = screenHeight - 25;
            int bountyX = screenWidth - 120;
            int statusX = screenWidth - 160;
            int statusY = bountyY - 12;

            Player player = client.player;

            if (isNearModDoor(player)) {
                Component nearDoorText = Component.translatable("hud.noellesroles.meatball.near_door")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                guiGraphics.drawString(client.font, nearDoorText, statusX, statusY, 0xFF5555);
            }

            // 赏金显示
            if (bounty > 0) {
                Component bountyText = Component.translatable("hud.noellesroles.meatball.bounty", bounty)
                        .withStyle(ChatFormatting.GOLD);
                guiGraphics.drawString(client.font, bountyText, bountyX, bountyY, 0xFFFFFF);
            } else {
                Component noBountyText = Component.translatable("hud.noellesroles.meatball.no_bounty")
                        .withStyle(ChatFormatting.GRAY);
                guiGraphics.drawString(client.font, noBountyText, bountyX, bountyY, 0xAAAAAA);
            }
        });
    }

    private static boolean isNearModDoor(Player player) {
        if (player.level() == null) return false;
        BlockPos playerPos = player.blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos checkPos = playerPos.offset(dx, dy, dz);
                    double dist = Math.sqrt(
                            (checkPos.getX() + 0.5 - player.getX()) * (checkPos.getX() + 0.5 - player.getX()) +
                            (checkPos.getY() + 0.5 - player.getY()) * (checkPos.getY() + 0.5 - player.getY()) +
                            (checkPos.getZ() + 0.5 - player.getZ()) * (checkPos.getZ() + 0.5 - player.getZ()));
                    if (dist <= DOOR_CHECK_RANGE) {
                        if (player.level().getBlockState(checkPos).getBlock() instanceof SmallDoorBlock) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}

package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
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

    private static final double ALONE_RANGE = 4.0;
    private static final double ALONE_HEIGHT = 3.0;
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

            int centerX = screenWidth / 2;
            int statusY = screenHeight / 2 + 30;
            int bountyY = screenHeight - 25;
            int bountyX = screenWidth - 120;

            Player player = client.player;
            var gameWorld = SREGameWorldComponent.KEY.get(player.level());

            // 检测是否靠近门框
            boolean nearDoor = isNearModDoor(player);

            if (nearDoor) {
                Component nearDoorText = Component.translatable("hud.noellesroles.meatball.near_door")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                guiGraphics.drawCenteredString(client.font, nearDoorText, centerX, statusY, 0xFF5555);
            } else {
                // 检测独处状态
                boolean isAlone = isAlone(player, gameWorld);

                if (isAlone) {
                    Component aloneText = Component.translatable("hud.noellesroles.meatball.alone")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                    guiGraphics.drawCenteredString(client.font, aloneText, centerX, statusY, 0xFF5555);
                } else {
                    Component notAloneText = Component.translatable("hud.noellesroles.meatball.not_alone")
                            .withStyle(ChatFormatting.GREEN);
                    guiGraphics.drawCenteredString(client.font, notAloneText, centerX, statusY, 0x55FF55);
                }
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

    private static boolean isAlone(Player player, SREGameWorldComponent gameWorld) {
        double safeDistanceSq = ALONE_RANGE * ALONE_RANGE;
        double safeHeightSq = ALONE_HEIGHT * ALONE_HEIGHT;

        for (Player nearbyPlayer : player.level().players()) {
            if (nearbyPlayer == player) continue;
            if (!GameUtils.isPlayerAliveAndSurvival(nearbyPlayer)) continue;

            double dx = nearbyPlayer.getX() - player.getX();
            double dy = nearbyPlayer.getY() - player.getY();
            double dz = nearbyPlayer.getZ() - player.getZ();
            double horizontalDistSq = dx * dx + dz * dz;

            if (horizontalDistSq <= safeDistanceSq && dy * dy <= safeHeightSq) {
                // 范围内有好人 → 不独处
                if (gameWorld.isInnocent(nearbyPlayer)) {
                    return false;
                }
            }
        }
        return true;
    }
}

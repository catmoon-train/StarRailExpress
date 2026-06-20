package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.event.CanCollideWith;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.world.entity.player.Player;

/**
 * 防止玩家叠叠乐的handler
 */
public class PlayerMountainHandler {
    public static boolean isOnOneHead(Player player, Player other) {
        var playerBox = player.getBoundingBox();
        var otherBox = other.getBoundingBox();

        // 水平重叠判断（XZ平面）
        boolean horizontalOverlap = playerBox.minX < otherBox.maxX && playerBox.maxX > otherBox.minX
                && playerBox.minZ < otherBox.maxZ && playerBox.maxZ > otherBox.minZ;
        boolean isOnHead = playerBox.minY >= otherBox.maxY - 0.1;
        if (horizontalOverlap && isOnHead) {
            return true;
        }
        return false;
    }

    public static void register() {
        CanCollideWith.PLAYER.register((player, entity) -> {
            if (entity instanceof Player other) {
                if (isOnOneHead(player, other)) {
                    return TrueFalseResult.TRUE;
                } else if (isOnOneHead(other, player)) {
                    return TrueFalseResult.TRUE;
                }
            }
            return TrueFalseResult.PASS;
        });
    }

}

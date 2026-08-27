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

package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.event.CanCollideWith;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.world.entity.player.Player;

import java.util.Random;

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

    /**
     * 将两个玩家水平推开（仅 XZ 平面）
     * 
     * @param a 玩家 A（通常为上方玩家）
     * @param b 玩家 B（通常为下方玩家）
     */
    public static Random random = new Random();

    public static void pushApart(Player a, Player b, double force) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        long time = a.level().getGameTime();
        if (dist < 0.01) {
            dx = 1d * (time / 10 % 2 == 1 ? 1d : -1d);
            dz = 1d * (time / 10 % 2 == 0 ? 1d : -1d); // 防止零向量
        } else {
            dx /= dist;
            dz /= dist;
        }
        a.push(dx * force, 0.0, dz * force);
        b.push(-dx * force, 0.0, -dz * force);
    }

    static final double pushForce = 0.05;

    public static void register() {
        CanCollideWith.PLAYER.register((player, entity) -> {
            if (GameUtils.isGameRunning(player)) {
                if (SREConfig.instance().disablePlayerMountain) {
                    if (entity instanceof Player other) {
                        if (isOnOneHead(player, other) || isOnOneHead(other, player)) {
                            pushApart(player, other, pushForce);
                            // 追踪伤害
                            player.setLastHurtByMob(other);
                            other.setLastHurtByMob(player);
                            return TrueFalseResult.FALSE;
                        }
                    }
                }
            }
            return TrueFalseResult.PASS;
        });
    }

}

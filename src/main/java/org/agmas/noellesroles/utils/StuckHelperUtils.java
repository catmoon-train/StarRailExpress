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

package org.agmas.noellesroles.utils;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class StuckHelperUtils {

    public static final double volumeThreshold = 0.03;

    public static boolean isPlayerStuck(Player player) {
        if (player.getVehicle() != null)
            return false;
        if (player.isSleeping())
            return false;
        Level level = player.level();
        AABB playerBox = player.getBoundingBox();

        return level.collidesWithSuffocatingBlock(player, playerBox);
    }
}

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

import io.wifi.starrailexpress.util.CantRightClickBlocks;

public class RightClickBlockManager {
    public static void init() {
        // 不能交互的方块
        CantRightClickBlocks.CANNOT_INTERACT_IDS.add("supplementaries:doormat");
        CantRightClickBlocks.CANNOT_INTERACT_IDS.add("handcrafted:oven");
    }
}

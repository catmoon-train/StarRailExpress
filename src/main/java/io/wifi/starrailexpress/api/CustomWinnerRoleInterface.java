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

package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.server.level.ServerPlayer;

/**
 * CustomWinnerRoleInterface
 */
public interface CustomWinnerRoleInterface {

    default WinStatus checkWin(ServerPlayer player, WinStatus winStatus) {
        return WinStatus.NOT_MODIFY;
    };

    /**
     * 玩家是否获胜。在获胜统计时被调用。
     */
    default boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) {
        return original;
    }
}

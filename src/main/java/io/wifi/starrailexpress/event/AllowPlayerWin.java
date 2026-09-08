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

package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface AllowPlayerWin {

    /**
     * 玩家是否获胜
     */
    Event<AllowPlayerWin> EVENT = createArrayBacked(AllowPlayerWin.class,
            listeners -> (world, player, playerRole,
                    winStatus, roundEnd,
                    gameComponent) -> {
                for (AllowPlayerWin listener : listeners) {
                    TrueFalseResult result = listener.allowPlayerWin(world, player, playerRole,
                            winStatus, roundEnd,
                            gameComponent);
                    if (result != null && result != TrueFalseResult.PASS) {
                        return result;
                    }
                }
                return TrueFalseResult.PASS;
            });

    /**
     * 玩家是否获胜
     */
    TrueFalseResult allowPlayerWin(ServerLevel world, ServerPlayer player, SRERole playerRole, WinStatus winStatus,
            SREGameRoundEndComponent roundEnd,
            SREGameWorldComponent gameComponent);
}

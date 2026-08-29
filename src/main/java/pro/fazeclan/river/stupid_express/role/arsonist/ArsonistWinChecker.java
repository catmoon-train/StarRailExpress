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

package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.server.level.ServerPlayer;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

public class ArsonistWinChecker {
    public static void registerEvent() {
        AllowGameEnd.EVENT_END.register((serverWorld, winStatus, isLooseEndsMode) -> {
            if (isLooseEndsMode)
                return WinStatus.NOT_MODIFY;
            var config = StupidExpress.CONFIG;
            var gameWorldComponent = SREGameWorldComponent.KEY.get(serverWorld);
            if (config.rolesSection.arsonistSection.arsonistKeepsGameGoing) {
                var players = serverWorld.getPlayers(GameUtils::isPlayerAliveAndSurvival);
                boolean arsonistAlive = false;
                for (ServerPlayer player : players) {
                    if (gameWorldComponent.isRole(player, SERoles.ARSONIST)) {
                        arsonistAlive = true;
                    }
                }

                if (players.size() == 1 && arsonistAlive) {
                    // 纵火犯独立胜利前，先检查布谷鸟是否满足条件（布谷鸟优先级大于纵火犯）
                    if (org.agmas.noellesroles.role_data.neutral.CuckooRoleData.checkCuckooVictory(serverWorld)) {
                        return GameUtils.WinStatus.CUSTOM;
                    }
                    // 纵火犯独立胜利统计：使用 RoleUtils.customWinnerWin
                    StupidRoleUtils.customWinnerWin(serverWorld, GameUtils.WinStatus.CUSTOM,
                            SERoles.ARSONIST.identifier().getPath(),
                            java.util.OptionalInt.of(SERoles.ARSONIST.color()));
                    return GameUtils.WinStatus.CUSTOM;
                }

                if (arsonistAlive && (winStatus == GameUtils.WinStatus.KILLERS
                        || winStatus == GameUtils.WinStatus.PASSENGERS)) {
                    return GameUtils.WinStatus.NONE;
                }
            }
            return GameUtils.WinStatus.NOT_MODIFY;
        });
    }
}

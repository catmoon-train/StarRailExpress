package org.agmas.noellesroles;

import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.thief.ThiefPlayerComponent;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;

public class CustomWinnerClass {

    public static void registerCustomWinners() {
        AllowGameEnd.EVENT.register((serverLevel, winStatus, isLooseEnd) -> {
            if (isLooseEnd) {
                return WinStatus.NOT_MODIFY;
            }

            var gameComponent = SREGameWorldComponent.KEY.get(serverLevel);

            // 检查是否有小偷存活
            boolean hasThiefAlive = false;
            int thiefCount = 0;
            int alivePlayerCount = 0;
            for (var player : serverLevel.players()) {
                if (GameUtils.isPlayerAliveAndSurvival(player)) {
                    alivePlayerCount++;
                    if (gameComponent.isRole(player, ModRoles.THIEF)) {
                        hasThiefAlive = true;
                        thiefCount++;
                    }
                }
            }

            // 如果有小偷存活，检查小偷独立胜利条件
            if (hasThiefAlive) {
                // 检查小偷是否满足独立胜利条件
                if (ThiefPlayerComponent.checkThiefVictory(serverLevel)) {
                    return WinStatus.CUSTOM;
                }

                // 如果小偷存活且游戏要结束（乘客或杀手胜利）
                if (winStatus.equals(WinStatus.PASSENGERS) ||
                    winStatus.equals(WinStatus.KILLERS)) {
                    // 如果场上只剩下小偷自己，按照乘客胜利结算
                    if (alivePlayerCount == thiefCount) {
                        // 只有小偷存活，按照乘客胜利结算
                        return WinStatus.PASSENGERS;
                    } else {
                        // 小偷和其他角色一起存活，阻止游戏结束
                        return WinStatus.NONE; // 游戏继续
                    }
                }
            }

            if (winStatus.equals(WinStatus.TIME) || winStatus.equals(WinStatus.PASSENGERS) || winStatus.equals(WinStatus.LOOSE_END)) {
                var players = serverLevel.players();
                for (var player : players) {
                    if (GameUtils.isPlayerAliveAndSurvival(player))
                        if (gameComponent.isRole(player, ModRoles.NIAN_SHOU)) {
                            // 年兽存活时，使用 RoleUtils.customWinnerWin 设置 CustomWinnerID
                            // RoleUtils.customWinnerWin(serverLevel, WinStatus.NIAN_SHOU, "nianshou", null);
                            return WinStatus.NIAN_SHOU;
                        }
                }
            }
            if (winStatus.equals(WinStatus.LOOSE_END)) {
                var players = serverLevel.players();
                for (var player : players) {
                    if (GameUtils.isPlayerAliveAndSurvival(player))
                        if (gameComponent.isRole(player, TMMRoles.LOOSE_END)) {
                            return WinStatus.LOOSE_END;
                        }
                }
                return WinStatus.PASSENGERS;
            }
            return WinStatus.NOT_MODIFY;
        });
    }
}

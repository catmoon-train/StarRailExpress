package org.agmas.noellesroles;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

import org.agmas.noellesroles.game.roles.neutral.candlebearer.CandleBearerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.cuckoo.CuckooPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.RedHouseRoles;
import org.agmas.noellesroles.utils.RoleUtils;

public class CustomWinnerClass {

    public static void registerCustomWinners() {
        AllowGameEnd.EVENT.register((serverLevel, winStatus, isLooseEnd) -> {
            if (isLooseEnd) {
                return WinStatus.NOT_MODIFY;
            }
            var refugeeCCA = RefugeeComponent.KEY.get(serverLevel);
            if(refugeeCCA.isPendingRestore){
                return WinStatus.NONE;
            }
            var gameComponent = SREGameWorldComponent.KEY.get(serverLevel);

            // 检查是否有小偷存活
            boolean hasFurandoru = false;
            boolean hasThiefAlive = false;
            // int thiefCount = 0;
            int alivePlayerCount = 0;
            for (var player : serverLevel.players()) {
                if (GameUtils.isPlayerAliveAndSurvival(player)) {
                    alivePlayerCount++;
                    SRERole role = gameComponent.getRole(player);
                    if (role != null) {
                        if (role instanceof CustomWinnerRole cwr) {
                            WinStatus resultWinStatus = cwr.checkWin(player, winStatus);
                            if (resultWinStatus != WinStatus.NOT_MODIFY) {
                                if (resultWinStatus == WinStatus.CUSTOM) {
                                    cwr.win(player);
                                }
                                return resultWinStatus;
                            }
                        }
                    }
                    if (gameComponent.isRole(player, ModRoles.THIEF)) {
                        hasThiefAlive = true;
                        // thiefCount++;
                    }
                    if (gameComponent.isRole(player, RedHouseRoles.FURANDORU)) {
                        hasFurandoru = true;
                        // thiefCount++;
                    }
                }
            }
            if (hasFurandoru) {
                if (alivePlayerCount <= 1 || winStatus.equals(WinStatus.TIME)) {
                    RoleUtils.customWinnerWin(serverLevel, "furandoru", RedHouseRoles.FURANDORU.color());
                    return WinStatus.CUSTOM;
                }
                if (!winStatus.equals(WinStatus.NONE))
                    return WinStatus.NONE;
            }
            // 如果有小偷存活，检查小偷独立胜利条件
            if (hasThiefAlive) {
                // 检查小偷是否满足独立胜利条件
                if (ThiefPlayerComponent.checkThiefVictory(serverLevel)) {
                    return WinStatus.CUSTOM;
                }

                // 如果小偷存活且游戏要结束（乘客或杀手胜利）
                // 注释：小偷不再阻止游戏结束
                // if (winStatus.equals(WinStatus.PASSENGERS) ||
                // winStatus.equals(WinStatus.KILLERS)) {
                // // 如果场上只剩下小偷自己，按照乘客胜利结算
                // if (alivePlayerCount == thiefCount) {
                // // 只有小偷存活，按照乘客胜利结算
                // return WinStatus.PASSENGERS;
                // } else {
                // // 小偷和其他角色一起存活，阻止游戏结束
                // return WinStatus.NONE; // 游戏继续
                // }
                // }
            }

            if (CandleBearerPlayerComponent.checkCandleBearerVictory(serverLevel)) {
                return WinStatus.CUSTOM;
            }

            // 布谷鸟胜利：在常规结局和年兽/纵火犯胜利时判定，优先级大于纵火犯和年兽
            if (winStatus.equals(WinStatus.PASSENGERS) || winStatus.equals(WinStatus.KILLERS) || winStatus.equals(WinStatus.TIME)
                    || winStatus.equals(WinStatus.NIAN_SHOU)) {
                if (CuckooPlayerComponent.checkCuckooVictory(serverLevel)) {
                    return WinStatus.CUSTOM;
                }
            }

            if (winStatus.equals(WinStatus.TIME) || winStatus.equals(WinStatus.PASSENGERS)
                    || winStatus.equals(WinStatus.LOOSE_END)) {
                var players = serverLevel.players();
                for (var player : players) {
                    if (GameUtils.isPlayerAliveAndSurvival(player))
                        if (gameComponent.isRole(player, ModRoles.NIAN_SHOU)) {
                            // 年兽存活时，使用 RoleUtils.customWinnerWin 设置 CustomWinnerID
                            // RoleUtils.customWinnerWin(serverLevel, WinStatus.NIAN_SHOU, "nianshou",
                            // null);
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

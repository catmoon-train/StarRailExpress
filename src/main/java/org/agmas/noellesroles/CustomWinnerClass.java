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

package org.agmas.noellesroles;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.CustomWinnerRoleInterface;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.ShouldRewardKillerTime;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.agmas.noellesroles.role_data.innocence.GhostRoleData;
import org.agmas.noellesroles.role_data.neutral.CandleBearerRoleData;
import org.agmas.noellesroles.role_data.neutral.CuckooRoleData;
import org.agmas.noellesroles.role_data.neutral.RavenRoleData;
import org.agmas.noellesroles.handler.utils.BeeFamilyManager;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

import java.util.OptionalInt;

public class CustomWinnerClass {

    public static WinStatus checkWinnerBuiltin(ServerLevel serverLevel, WinStatus winStatus, boolean isLooseEnd) {
        if (isLooseEnd) {
            return WinStatus.NOT_MODIFY;
        }
        var refugeeCCA = RefugeeComponent.KEY.get(serverLevel);
        if (refugeeCCA.isPendingRestore) {
            return WinStatus.NONE;
        }
        var gameComponent = SREGameWorldComponent.KEY.get(serverLevel);

        // 检查是否有小偷存活
        boolean hasFurandoru = false;
        boolean hasThiefAlive = false;
        boolean hasPelicanAlive = false;
        boolean hasMonokumaAlive = false;
        // int thiefCount = 0;
        int alivePlayerCount = 0;
        for (var player : serverLevel.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                alivePlayerCount++;
                SRERole role = gameComponent.getRole(player);
                if (role != null) {
                    if (role instanceof CustomWinnerRoleInterface cwr) {
                        WinStatus resultWinStatus = cwr.checkWin(player, winStatus);
                        if (resultWinStatus != WinStatus.NOT_MODIFY) {
                            if (resultWinStatus == WinStatus.CUSTOM) {
                                if (cwr instanceof CustomWinnerRole w)
                                    w.win(player);
                            }
                            return resultWinStatus;
                        }
                    }
                }
                if (gameComponent.isRole(player, ModRoles.THIEF)) {
                    hasThiefAlive = true;
                }
                if (gameComponent.isRole(player, THRedHouseRoles.FURANDORU)) {
                    hasFurandoru = true;
                }
                if (gameComponent.isRole(player, ModRoles.PELICAN)) {
                    hasPelicanAlive = true;
                }
                if (gameComponent.isRole(player, ModRoles.MONOKUMA)) {
                    hasMonokumaAlive = true;
                }
            }
        }
        if (hasFurandoru) {
            if (alivePlayerCount <= 1 || winStatus.equals(WinStatus.TIME)) {
                RoleUtils.customWinnerWin(serverLevel, "furandoru", THRedHouseRoles.FURANDORU.color());
                return WinStatus.CUSTOM;
            }
            if (!winStatus.equals(WinStatus.NONE))
                return WinStatus.NONE;
        }
        // 如果有小偷存活，检查小偷独立胜利条件
        if (hasThiefAlive) {
            // 检查小偷是否满足独立胜利条件
            if (org.agmas.noellesroles.role_data.neutral.ThiefRoleData.checkThiefVictory(serverLevel)) {
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

        if (CandleBearerRoleData.checkCandleBearerVictory(serverLevel)) {
            return WinStatus.CUSTOM;
        }

        if (org.agmas.noellesroles.role_data.neutral.DoomedSinnerRoleData
                .checkDoomedSinnerVictory(serverLevel)) {
            return WinStatus.CUSTOM;
        }

        if (org.agmas.noellesroles.role_data.neutral.LinFamilyRoleData
                .checkLinFamilyVictory(serverLevel)) {
            return WinStatus.CUSTOM;
        }

        for (ServerPlayer player : serverLevel.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player) || !gameComponent.isRole(player, ModRoles.RAVEN))
                continue;
            RavenRoleData raven = io.wifi.starrailexpress.api.data.RoleData.getNullable(RavenRoleData.class, player);
            if (raven != null && raven.kills >= raven.requiredKills && raven.requiredKills > 0) {
                RoleUtils.customWinnerWin(serverLevel, WinStatus.CUSTOM, ModRoles.RAVEN_ID.getPath(),
                        OptionalInt.of(ModRoles.RAVEN.color()));
                return WinStatus.CUSTOM;
            }
        }

        // 阿蒙「终幕·寻找阿蒙」：存在持有寄宿体的存活阿蒙时进入终幕并阻止常规结算；
        // 终幕结束（撑过 2 分钟或杀光众人）由组件自身宣布 CUSTOM 胜利。
        WinStatus amonResult = org.agmas.noellesroles.role_data.neutral.AmonRoleData
                .handleGameEnd(serverLevel, winStatus);
        if (amonResult != WinStatus.NOT_MODIFY) {
            return amonResult;
        }

        // 鹈鹕存活时检查独立胜利
        if (org.agmas.noellesroles.role_data.neutral.PelicanRoleData.checkPelicanVictory(serverLevel)) {
            return WinStatus.CUSTOM;
        }

        // 蜜蜂家族独立胜利
        if (BeeFamilyManager.checkBeeFamilyVictory(serverLevel)) {
            return WinStatus.CUSTOM;
        }

        // 教父存活时阻止游戏结束
        if (BeeFamilyManager.shouldPreventGameEnd(serverLevel)
                && (winStatus == WinStatus.KILLERS || winStatus == WinStatus.PASSENGERS)) {
            return WinStatus.NONE;
        }
        // 教父家族独立胜利
        if (org.agmas.noellesroles.game.roles.neutral.mafia.MafiaManager.checkMafiaVictory(serverLevel)) {
            return WinStatus.CUSTOM;
        }
        // 教父存活时阻止游戏结束
        if (org.agmas.noellesroles.game.roles.neutral.mafia.MafiaManager.shouldPreventGameEnd(serverLevel)
                && (winStatus == WinStatus.KILLERS || winStatus == WinStatus.PASSENGERS)) {
            return WinStatus.NONE;
        }
        // 鹈鹕是唯一存活玩家时独立胜利（仅鹈鹕存活，或仅鹈鹕+黑白存活）
        if (hasPelicanAlive && (alivePlayerCount == 1 || (alivePlayerCount == 2 && hasMonokumaAlive))) {
            RoleUtils.customWinnerWin(serverLevel,
                    ModRoles.PELICAN_ID.getPath(),
                    ModRoles.PELICAN.color());
            return WinStatus.CUSTOM;
        }
        // 鹈鹕存活时阻止乘客/杀手胜利导致游戏结束（参考纵火犯）
        if (hasPelicanAlive && (winStatus == WinStatus.KILLERS || winStatus == WinStatus.PASSENGERS)) {
            return WinStatus.NONE;
        }

        // 布谷鸟胜利：在常规结局和年兽/纵火犯胜利时判定，优先级大于纵火犯和年兽
        if (winStatus.equals(WinStatus.PASSENGERS) || winStatus.equals(WinStatus.KILLERS)
                || winStatus.equals(WinStatus.TIME)
                || winStatus.equals(WinStatus.NIAN_SHOU)) {
            if (CuckooRoleData.checkCuckooVictory(serverLevel)) {
                return WinStatus.CUSTOM;
            }
        }

        // 自定义角色独立胜利判定 (来自 CustomRoleLoader)
        WinStatus customRoleWin = io.wifi.starrailexpress.customrole.CustomRoleLoader
                .checkCustomRoleWins(serverLevel, winStatus);
        if (customRoleWin != WinStatus.NOT_MODIFY) {
            return customRoleWin;
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

    }

    public static void registerEvents() {
        // 如果小透明/芙兰已经通知过了，那就杀人不再增加时间。
        ShouldRewardKillerTime.EVENT.register((victim, killer, deathReason, forceKill, spawnBody) -> {
            for (final var p : victim.level().players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(p))
                    continue;
                if (p.getUUID().equals(victim.getUUID()))
                    continue;
                if (RoleData.getNullable(p) instanceof GhostRoleData grd) {
                    {
                        if (grd.lastStandNotified) {
                            return TrueFalseResult.FALSE;
                        }
                    }
                }
            }
            return TrueFalseResult.PASS;
        });
    }
}

package net.exmo.sre.repair.logic;

import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.repair.component.RepairRolePlayerComponent;
import net.exmo.sre.repair.state.RepairModeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;

/**
 * 修机模式胜负判定，按优先级依次判：
 * 中立角色达成个人目标 → 自定义胜利；
 * 猎人全灭 → 逃脱者胜；
 * 场上一个逃脱者都不剩 → 有人逃出去算逃脱者胜，否则猎人胜；
 * 场上剩下的逃脱者全部失去行动能力（倒地 / 被扛 / 挂在审判笼上）→ 猎人胜；
 * 时间耗尽 → 猎人胜。
 *
 * "逃脱者" = 所有非猎人阵营的人（幸存者 + 中立）。只统计幸存者会让残局里最后一个中立
 * 被无视，也会漏掉倒地以外的失能状态，那正是"所有人倒地后游戏不结束"的来源。
 */
public final class RepairWinConditions {
    private RepairWinConditions() {
    }

    public static void tick(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (gameWorldComponent.getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) {
            return;
        }

        ServerPlayer neutralWinner = null;
        int livingHunters = 0;
        int escaped = 0;
        int inPlay = 0;
        int incapacitated = 0;

        for (ServerPlayer player : serverWorld.players()) {
            if (player.getTags().contains(RepairModeState.NEUTRAL_WIN_TAG)) {
                neutralWinner = player;
                break;
            }
            // 逃出大门的人会被切成旁观，必须抢在"已淘汰"判定之前统计
            if (player.getTags().contains(RepairModeState.ESCAPED_TAG)) {
                if (RepairModeState.isNonHunterRepairPlayer(player)) {
                    escaped++;
                }
                continue;
            }
            if (GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            if (RepairModeState.isHunter(player)) {
                livingHunters++;
                continue;
            }
            if (!RepairModeState.isNonHunterRepairPlayer(player)) {
                continue;
            }
            inPlay++;
            if (isIncapacitated(ModComponents.REPAIR_ROLES.get(player))) {
                incapacitated++;
            }
        }

        GameUtils.WinStatus winStatus = resolve(serverWorld, neutralWinner != null, livingHunters, escaped, inPlay,
                incapacitated);
        if (winStatus == GameUtils.WinStatus.NONE) {
            return;
        }

        SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
        if (neutralWinner != null) {
            roundEnd.CustomWinnerID = ModComponents.REPAIR_ROLES.get(neutralWinner).activeRole;
            roundEnd.CustomWinnerPlayers.add(neutralWinner.getUUID());
        }
        roundEnd.setRoundEndData(serverWorld.players(), winStatus);
        GameUtils.stopGame(serverWorld);
    }

    /** 倒地、被猎人扛着、挂在审判笼上的人都没法再修机或逃脱，等同出局。 */
    private static boolean isIncapacitated(RepairRolePlayerComponent component) {
        return component.downed || component.carriedBy != null || component.trialStand.present();
    }

    private static GameUtils.WinStatus resolve(ServerLevel serverWorld, boolean neutralWon, int livingHunters,
            int escaped, int inPlay, int incapacitated) {
        if (neutralWon) {
            return GameUtils.WinStatus.CUSTOM;
        }
        if (livingHunters == 0) {
            return GameUtils.WinStatus.PASSENGERS;
        }
        if (inPlay == 0) {
            return escaped > 0 ? GameUtils.WinStatus.PASSENGERS : GameUtils.WinStatus.KILLERS;
        }
        if (incapacitated >= inPlay) {
            return GameUtils.WinStatus.KILLERS;
        }
        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime()) {
            return GameUtils.WinStatus.KILLERS;
        }
        return GameUtils.WinStatus.NONE;
    }
}

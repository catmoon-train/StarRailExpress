package net.exmo.sre.repair.logic;

import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.repair.role.RepairRoleDefinition;
import net.exmo.sre.repair.state.RepairModeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.game_spec.RepairRoles;

/**
 * 修机模式胜负判定：
 * 中立角色达成个人目标 → 自定义胜利；
 * 有人逃脱且场上无存活幸存者 → 幸存者胜；
 * 幸存者全灭或全部倒地、或时间耗尽 → 猎人胜；
 * 猎人全灭 → 幸存者胜。
 */
public final class RepairWinConditions {
    private RepairWinConditions() {
    }

    public static void tick(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;
        int activeSurvivors = 0;
        int escapedSurvivors = 0;
        int livingHunters = 0;

        for (ServerPlayer player : serverWorld.players()) {
            var component = ModComponents.REPAIR_ROLES.get(player);
            if (player.getTags().contains(RepairModeState.NEUTRAL_WIN_TAG)) {
                var roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
                roundEnd.CustomWinnerID = component.activeRole;
                roundEnd.CustomWinnerPlayers.add(player.getUUID());
                winStatus = GameUtils.WinStatus.CUSTOM;
                break;
            }

            boolean hunter = RepairRoleDefinition.byId(component.activeRole)
                    .map(role -> role.faction == RepairRoleDefinition.Faction.HUNTER)
                    .orElse(gameWorldComponent.isRole(player, RepairRoles.REPAIR_HUNTER));
            if (hunter) {
                if (!GameUtils.isPlayerEliminated(player)) {
                    livingHunters++;
                }
                continue;
            }

            boolean survivor = RepairRoleDefinition.byId(component.activeRole)
                    .map(role -> role.faction == RepairRoleDefinition.Faction.SURVIVOR)
                    .orElse(gameWorldComponent.isRole(player, RepairRoles.REPAIR_SURVIVOR));
            if (!survivor) {
                continue;
            }
            if (player.getTags().contains(RepairModeState.ESCAPED_TAG)) {
                escapedSurvivors++;
            } else if (!GameUtils.isPlayerEliminated(player)) {
                activeSurvivors++;
            }
        }

        // 检查是否所有幸存者都倒地了
        int totalSurvivors = 0;
        int downedSurvivors = 0;
        for (ServerPlayer player : serverWorld.players()) {
            var component = ModComponents.REPAIR_ROLES.get(player);
            boolean survivor = RepairRoleDefinition.byId(component.activeRole)
                    .map(role -> role.faction == RepairRoleDefinition.Faction.SURVIVOR)
                    .orElse(gameWorldComponent.isRole(player, RepairRoles.REPAIR_SURVIVOR));
            if (survivor && !player.getTags().contains(RepairModeState.ESCAPED_TAG)
                    && !GameUtils.isPlayerEliminated(player)) {
                totalSurvivors++;
                if (component.downed) {
                    downedSurvivors++;
                }
            }
        }

        if (winStatus == GameUtils.WinStatus.NONE) {
            if (escapedSurvivors > 0 && activeSurvivors == 0) {
                winStatus = GameUtils.WinStatus.PASSENGERS;
            } else if (activeSurvivors == 0 || downedSurvivors >= totalSurvivors) {
                // 所有幸存者都倒地或被消除，猎人胜利
                winStatus = GameUtils.WinStatus.KILLERS;
            } else if (livingHunters == 0) {
                winStatus = GameUtils.WinStatus.PASSENGERS;
            } else if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime()) {
                winStatus = GameUtils.WinStatus.KILLERS;
            }
        }

        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }
}

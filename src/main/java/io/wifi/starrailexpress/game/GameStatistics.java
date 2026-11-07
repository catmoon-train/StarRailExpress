/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.stats.PlayerStats;
import io.wifi.starrailexpress.stats.PlayerStatsManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.UUID;

import static io.wifi.starrailexpress.game.GameUtils.*;

/** 击杀历史、参赛统计及胜负统计；沿用门面中的唯一击杀记录表。 */
final class GameStatistics {
    private GameStatistics() {
    }

    static void recordPlayerKill(Player killer, Player victim, ResourceLocation reason, boolean haveDead) {
        if (victim instanceof ServerPlayer svictim) {
            if (killer == null) {
                GameUtils.recordPlayerKillServer(null, svictim, reason, haveDead);
            } else if (killer instanceof ServerPlayer skiller) {
                GameUtils.recordPlayerKillServer(skiller, svictim, reason, haveDead);
            }
        }
    }

    static void recordPlayerKillServer(ServerPlayer killer, ServerPlayer victim, ResourceLocation reason,
            boolean haveDead) {
        if (victim == null) return;
        if (killer != null) serverCacheKillState.putIfAbsent(killer.getUUID(), new PlayerKillInfo());
        serverCacheKillState.putIfAbsent(victim.getUUID(), new PlayerKillInfo());
        PlayerKillInfo killerInfo = killer != null ? serverCacheKillState.get(killer.getUUID()) : null;
        PlayerKillInfo victimInfo = serverCacheKillState.get(victim.getUUID());
        var info = new PlayerKillResultInfo(victim.level().getGameTime(), haveDead,
                killer == null ? null : killer.getUUID(), victim.getUUID(), reason);
        if (killerInfo != null) killerInfo.recordKill(info);
        victimInfo.recordDeath(info);
    }

    static PlayerKillInfo getPlayerKillsInfo(UUID player) {
        return player == null ? null : serverCacheKillState.getOrDefault(player, null);
    }

    static PlayerKillInfo getPlayerKillsInfo(Player player) {
        return player == null ? null : serverCacheKillState.getOrDefault(player.getUUID(), null);
    }

    static PlayerKillResultInfo getPlayerLastDeathInfo(UUID player) {
        if (player == null) return null;
        var info = serverCacheKillState.getOrDefault(player, null);
        return info == null ? null : info.getLastDeathInfo();
    }

    static PlayerKillResultInfo getPlayerLastDeathInfo(Player player) {
        return player == null ? null : getPlayerLastDeathInfo(player.getUUID());
    }

    static PlayerKillResultInfo getPlayerLastKillInfo(UUID player) {
        if (player == null) return null;
        var info = serverCacheKillState.getOrDefault(player, null);
        return info == null ? null : info.getLastKillInfo();
    }

    static PlayerKillResultInfo getPlayerLastKillInfo(Player player) {
        return player == null ? null : getPlayerLastKillInfo(player.getUUID());
    }

    static void recordPlayerStats(ServerLevel world, SREGameWorldComponent game, ArrayList<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            PlayerStats stats = PlayerStatsManager.get(player);
            stats.incrementTotalGamesPlayed();
            SRERole role = game.getRole(player);
            if (role != null) {
                stats.getOrCreateRoleStats(role.identifier()).incrementTimesPlayed();
                if (role.isVigilanteTeam()) {
                    stats.incrementTotalSheriffGames();
                } else if (role.canUseKiller()) {
                    stats.incrementTotalKillerGames();
                } else if (role.isNeutrals()) {
                    stats.incrementTotalNeutralGames();
                } else if (role.isInnocent() && !role.isVigilanteTeam()) {
                    stats.incrementTotalCivilianGames();
                }
            }
        }
    }

    static void recordWinStats(ServerLevel world, GameMode mode, SREGameRoundEndComponent roundEnd,
            SREGameWorldComponent game) {
        WinStatus winStatus = roundEnd.getWinStatus();
        boolean isLoversWin = winStatus == WinStatus.LOVERS;
        for (ServerPlayer player : world.players()) {
            PlayerStats stats = PlayerStatsManager.get(player);
            SRERole role = game.getRole(player);
            SRERole allegiance = game.getAllegianceRole(player);
            boolean isWinner = mode.isPlayerWinning(world, player, role, roundEnd, game);
            if (isWinner) {
                roundEnd.setPlayerWin(player.getUUID(), isWinner);
                roundEnd.CustomWinnerPlayers.add(player.getUUID());
                stats.incrementTotalWins();
                if (role != null) {
                    stats.getOrCreateRoleStats(role.identifier()).incrementWinsAsRole();
                    if (allegiance.isVigilanteTeam()) {
                        stats.incrementTotalSheriffWins();
                    } else if (allegiance.canUseKiller()) {
                        stats.incrementTotalKillerWins();
                    } else if (allegiance.isNeutrals()) {
                        stats.incrementTotalNeutralWins();
                    } else if (allegiance.isInnocent() && !allegiance.isVigilanteTeam()) {
                        stats.incrementTotalCivilianWins();
                    }
                }
                if (isLoversWin && roundEnd.CustomWinnerPlayers != null
                        && roundEnd.CustomWinnerPlayers.contains(player.getUUID())) {
                    stats.incrementTotalLoversWins();
                    ProgressionDataManager.onLoversWin(player);
                }
                if (allegiance != null && allegiance.isNeutrals()
                        && (winStatus == WinStatus.CUSTOM || winStatus == WinStatus.GAMBLER
                                || winStatus == WinStatus.NIAN_SHOU || winStatus == WinStatus.RECORDER
                                || winStatus == WinStatus.LOOSE_END)) {
                    ProgressionDataManager.onNeutralIndependentWin(player);
                }
            } else {
                stats.incrementTotalLosses();
                if (role != null) stats.getOrCreateRoleStats(role.identifier()).incrementLossesAsRole();
            }
            ProgressionDataManager.onRoundSettled(player, allegiance, isWinner);
        }
    }
}

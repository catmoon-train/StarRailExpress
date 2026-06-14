package io.wifi.starrailexpress.util;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.wifi.starrailexpress.game.data.PlayerStatsData;
import io.wifi.starrailexpress.stats.PlayerStats;

import java.util.HashMap;
import java.util.Map;

public final class PlayerStatsSerializer {
    private static final Gson GSON = new Gson();

    private PlayerStatsSerializer() {
    }

    public static String toJson(PlayerStats stats) {
        return toJson(stats, 0L);
    }

    public static String toJson(PlayerStats stats, long updatedAt) {
        return GSON.toJson(toData(stats, updatedAt));
    }

    public static PlayerStatsData fromJson(String json) throws JsonSyntaxException {
        PlayerStatsData data = GSON.fromJson(json, PlayerStatsData.class);
        if (data == null) {
            throw new JsonSyntaxException("Player stats payload is null");
        }
        return data;
    }

    public static PlayerStatsData toData(PlayerStats stats, long updatedAt) {
        PlayerStatsData data = new PlayerStatsData();
        data.setUuid(stats.getPlayerUuid().toString());
        data.setUpdatedAt(updatedAt);
        data.setTotalPlayTime(stats.getTotalPlayTime());
        data.setTotalGamesPlayed(stats.getTotalGamesPlayed());
        data.setTotalKills(stats.getTotalKills());
        data.setTotalDeaths(stats.getTotalDeaths());
        data.setTotalWins(stats.getTotalWins());
        data.setTotalLosses(stats.getTotalLosses());
        data.setTotalTeamKills(stats.getTotalTeamKills());
        data.setTotalLoversWins(stats.getTotalLoversWins());
        data.setTotalCivilianGames(stats.getTotalCivilianGames());
        data.setTotalCivilianWins(stats.getTotalCivilianWins());
        data.setTotalCivilianKills(stats.getTotalCivilianKills());
        data.setTotalCivilianDeaths(stats.getTotalCivilianDeaths());
        data.setTotalKillerGames(stats.getTotalKillerGames());
        data.setTotalKillerWins(stats.getTotalKillerWins());
        data.setTotalKillerKills(stats.getTotalKillerKills());
        data.setTotalKillerDeaths(stats.getTotalKillerDeaths());
        data.setTotalNeutralGames(stats.getTotalNeutralGames());
        data.setTotalNeutralWins(stats.getTotalNeutralWins());
        data.setTotalNeutralKills(stats.getTotalNeutralKills());
        data.setTotalNeutralDeaths(stats.getTotalNeutralDeaths());
        data.setTotalSheriffGames(stats.getTotalSheriffGames());
        data.setTotalSheriffWins(stats.getTotalSheriffWins());
        data.setTotalSheriffKills(stats.getTotalSheriffKills());
        data.setTotalSheriffDeaths(stats.getTotalSheriffDeaths());

        Map<String, PlayerStatsData.RoleStatsData> roles = new HashMap<>();
        stats.getRoleStats().forEach((roleId, roleStats) -> {
            PlayerStatsData.RoleStatsData roleData = new PlayerStatsData.RoleStatsData();
            roleData.setTimesPlayed(roleStats.getTimesPlayed());
            roleData.setKillsAsRole(roleStats.getKillsAsRole());
            roleData.setDeathsAsRole(roleStats.getDeathsAsRole());
            roleData.setWinsAsRole(roleStats.getWinsAsRole());
            roleData.setLossesAsRole(roleStats.getLossesAsRole());
            roleData.setTeamKillsAsRole(roleStats.getTeamKillsAsRole());
            roles.put(roleId.toString(), roleData);
        });
        data.setRoleStats(roles);
        return data;
    }
}

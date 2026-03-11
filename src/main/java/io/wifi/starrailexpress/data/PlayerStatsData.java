package io.wifi.starrailexpress.data;

import java.util.HashMap;
import java.util.Map;

/**
 * 玩家统计数据的数据传输对象 (DTO)
 * 用于 JSON 序列化和反序列化
 */
public class PlayerStatsData {
    private String uuid;
    private long totalPlayTime;
    private int totalGamesPlayed;
    private int totalKills;
    private int totalDeaths;
    private int totalWins;
    private int totalLosses;
    private int totalTeamKills;
    private int totalLoversWins;

    // 阵营统计数据
    private int totalCivilianGames = 0;
    private int totalCivilianWins = 0;
    private int totalCivilianKills = 0;
    private int totalCivilianDeaths = 0;
    private int totalKillerGames = 0;
    private int totalKillerWins = 0;
    private int totalKillerKills = 0;
    private int totalKillerDeaths = 0;
    private int totalNeutralGames = 0;
    private int totalNeutralWins = 0;
    private int totalNeutralKills = 0;
    private int totalNeutralDeaths = 0;
    private int totalSheriffGames = 0;
    private int totalSheriffWins = 0;
    private int totalSheriffKills = 0;
    private int totalSheriffDeaths = 0;

    private Map<String, RoleStatsData> roleStats = new HashMap<>();

    /**
     * 角色统计数据
     */
    public static class RoleStatsData {
        private int timesPlayed;
        private int killsAsRole;
        private int deathsAsRole;
        private int winsAsRole;
        private int lossesAsRole;
        private int teamKillsAsRole;

        // 默认构造函数用于 Gson
        public RoleStatsData() {}

        // Getter 和 Setter 方法
        public int getTimesPlayed() {
            return timesPlayed;
        }

        public void setTimesPlayed(int timesPlayed) {
            this.timesPlayed = timesPlayed;
        }

        public int getKillsAsRole() {
            return killsAsRole;
        }

        public void setKillsAsRole(int killsAsRole) {
            this.killsAsRole = killsAsRole;
        }

        public int getDeathsAsRole() {
            return deathsAsRole;
        }

        public void setDeathsAsRole(int deathsAsRole) {
            this.deathsAsRole = deathsAsRole;
        }

        public int getWinsAsRole() {
            return winsAsRole;
        }

        public void setWinsAsRole(int winsAsRole) {
            this.winsAsRole = winsAsRole;
        }

        public int getLossesAsRole() {
            return lossesAsRole;
        }

        public void setLossesAsRole(int lossesAsRole) {
            this.lossesAsRole = lossesAsRole;
        }

        public int getTeamKillsAsRole() {
            return teamKillsAsRole;
        }

        public void setTeamKillsAsRole(int teamKillsAsRole) {
            this.teamKillsAsRole = teamKillsAsRole;
        }
    }

    // 默认构造函数用于 Gson
    public PlayerStatsData() {}

    // Getter 和 Setter 方法
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public long getTotalPlayTime() {
        return totalPlayTime;
    }

    public void setTotalPlayTime(long totalPlayTime) {
        this.totalPlayTime = totalPlayTime;
    }

    public int getTotalGamesPlayed() {
        return totalGamesPlayed;
    }

    public void setTotalGamesPlayed(int totalGamesPlayed) {
        this.totalGamesPlayed = totalGamesPlayed;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public void setTotalKills(int totalKills) {
        this.totalKills = totalKills;
    }

    public int getTotalDeaths() {
        return totalDeaths;
    }

    public void setTotalDeaths(int totalDeaths) {
        this.totalDeaths = totalDeaths;
    }

    public int getTotalWins() {
        return totalWins;
    }

    public void setTotalWins(int totalWins) {
        this.totalWins = totalWins;
    }

    public int getTotalLosses() {
        return totalLosses;
    }

    public void setTotalLosses(int totalLosses) {
        this.totalLosses = totalLosses;
    }

    public int getTotalTeamKills() {
        return totalTeamKills;
    }

    public void setTotalTeamKills(int totalTeamKills) {
        this.totalTeamKills = totalTeamKills;
    }

    public int getTotalLoversWins() {
        return totalLoversWins;
    }

    public void setTotalLoversWins(int totalLoversWins) {
        this.totalLoversWins = totalLoversWins;
    }

    // 阵营统计 Getter 和 Setter 方法
    public int getTotalCivilianGames() {
        return totalCivilianGames;
    }

    public void setTotalCivilianGames(int totalCivilianGames) {
        this.totalCivilianGames = totalCivilianGames;
    }

    public int getTotalCivilianWins() {
        return totalCivilianWins;
    }

    public void setTotalCivilianWins(int totalCivilianWins) {
        this.totalCivilianWins = totalCivilianWins;
    }

    public int getTotalCivilianKills() {
        return totalCivilianKills;
    }

    public void setTotalCivilianKills(int totalCivilianKills) {
        this.totalCivilianKills = totalCivilianKills;
    }

    public int getTotalCivilianDeaths() {
        return totalCivilianDeaths;
    }

    public void setTotalCivilianDeaths(int totalCivilianDeaths) {
        this.totalCivilianDeaths = totalCivilianDeaths;
    }

    public int getTotalKillerGames() {
        return totalKillerGames;
    }

    public void setTotalKillerGames(int totalKillerGames) {
        this.totalKillerGames = totalKillerGames;
    }

    public int getTotalKillerWins() {
        return totalKillerWins;
    }

    public void setTotalKillerWins(int totalKillerWins) {
        this.totalKillerWins = totalKillerWins;
    }

    public int getTotalKillerKills() {
        return totalKillerKills;
    }

    public void setTotalKillerKills(int totalKillerKills) {
        this.totalKillerKills = totalKillerKills;
    }

    public int getTotalKillerDeaths() {
        return totalKillerDeaths;
    }

    public void setTotalKillerDeaths(int totalKillerDeaths) {
        this.totalKillerDeaths = totalKillerDeaths;
    }

    public int getTotalNeutralGames() {
        return totalNeutralGames;
    }

    public void setTotalNeutralGames(int totalNeutralGames) {
        this.totalNeutralGames = totalNeutralGames;
    }

    public int getTotalNeutralWins() {
        return totalNeutralWins;
    }

    public void setTotalNeutralWins(int totalNeutralWins) {
        this.totalNeutralWins = totalNeutralWins;
    }

    public int getTotalNeutralKills() {
        return totalNeutralKills;
    }

    public void setTotalNeutralKills(int totalNeutralKills) {
        this.totalNeutralKills = totalNeutralKills;
    }

    public int getTotalNeutralDeaths() {
        return totalNeutralDeaths;
    }

    public void setTotalNeutralDeaths(int totalNeutralDeaths) {
        this.totalNeutralDeaths = totalNeutralDeaths;
    }

    public int getTotalSheriffGames() {
        return totalSheriffGames;
    }

    public void setTotalSheriffGames(int totalSheriffGames) {
        this.totalSheriffGames = totalSheriffGames;
    }

    public int getTotalSheriffWins() {
        return totalSheriffWins;
    }

    public void setTotalSheriffWins(int totalSheriffWins) {
        this.totalSheriffWins = totalSheriffWins;
    }

    public int getTotalSheriffKills() {
        return totalSheriffKills;
    }

    public void setTotalSheriffKills(int totalSheriffKills) {
        this.totalSheriffKills = totalSheriffKills;
    }

    public int getTotalSheriffDeaths() {
        return totalSheriffDeaths;
    }

    public void setTotalSheriffDeaths(int totalSheriffDeaths) {
        this.totalSheriffDeaths = totalSheriffDeaths;
    }

    public Map<String, RoleStatsData> getRoleStats() {
        return roleStats;
    }

    public void setRoleStats(Map<String, RoleStatsData> roleStats) {
        this.roleStats = roleStats;
    }
}
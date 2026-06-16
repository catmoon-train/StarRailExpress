package io.wifi.starrailexpress.stats;

import io.wifi.starrailexpress.game.data.PlayerStatsData;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 纯内存玩家统计对象。持久化和网络同步由 PlayerStatsManager 负责。
 */
public final class PlayerStats {
    private final UUID playerUuid;
    private long totalPlayTime;
    private int totalGamesPlayed;
    private int totalKills;
    private int totalDeaths;
    private int totalWins;
    private int totalLosses;
    private int totalTeamKills;
    private int totalLoversWins;
    private int totalCivilianGames;
    private int totalCivilianWins;
    private int totalCivilianKills;
    private int totalCivilianDeaths;
    private int totalKillerGames;
    private int totalKillerWins;
    private int totalKillerKills;
    private int totalKillerDeaths;
    private int totalNeutralGames;
    private int totalNeutralWins;
    private int totalNeutralKills;
    private int totalNeutralDeaths;
    private int totalSheriffGames;
    private int totalSheriffWins;
    private int totalSheriffKills;
    private int totalSheriffDeaths;
    private final Map<ResourceLocation, RoleStats> roleStats = new HashMap<>();
    private transient Runnable dirtyListener = () -> {
    };

    public PlayerStats(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {
        } : dirtyListener;
    }

    private void markDirty() {
        dirtyListener.run();
    }

    public void replaceWith(PlayerStatsData data) {
        if (data == null) {
            return;
        }
        totalPlayTime = data.getTotalPlayTime();
        totalGamesPlayed = data.getTotalGamesPlayed();
        totalKills = data.getTotalKills();
        totalDeaths = data.getTotalDeaths();
        totalWins = data.getTotalWins();
        totalLosses = data.getTotalLosses();
        totalTeamKills = data.getTotalTeamKills();
        totalLoversWins = data.getTotalLoversWins();
        totalCivilianGames = data.getTotalCivilianGames();
        totalCivilianWins = data.getTotalCivilianWins();
        totalCivilianKills = data.getTotalCivilianKills();
        totalCivilianDeaths = data.getTotalCivilianDeaths();
        totalKillerGames = data.getTotalKillerGames();
        totalKillerWins = data.getTotalKillerWins();
        totalKillerKills = data.getTotalKillerKills();
        totalKillerDeaths = data.getTotalKillerDeaths();
        totalNeutralGames = data.getTotalNeutralGames();
        totalNeutralWins = data.getTotalNeutralWins();
        totalNeutralKills = data.getTotalNeutralKills();
        totalNeutralDeaths = data.getTotalNeutralDeaths();
        totalSheriffGames = data.getTotalSheriffGames();
        totalSheriffWins = data.getTotalSheriffWins();
        totalSheriffKills = data.getTotalSheriffKills();
        totalSheriffDeaths = data.getTotalSheriffDeaths();

        roleStats.clear();
        Map<String, PlayerStatsData.RoleStatsData> serializedRoles = data.getRoleStats();
        if (serializedRoles == null) {
            return;
        }
        serializedRoles.forEach((roleId, roleData) -> {
            ResourceLocation id = ResourceLocation.tryParse(roleId);
            if (id == null || roleData == null) {
                return;
            }
            RoleStats stats = new RoleStats();
            stats.timesPlayed = roleData.getTimesPlayed();
            stats.killsAsRole = roleData.getKillsAsRole();
            stats.deathsAsRole = roleData.getDeathsAsRole();
            stats.winsAsRole = roleData.getWinsAsRole();
            stats.lossesAsRole = roleData.getLossesAsRole();
            stats.teamKillsAsRole = roleData.getTeamKillsAsRole();
            roleStats.put(id, stats);
        });
    }

    public long getTotalPlayTime() {
        return totalPlayTime;
    }

    public void setTotalPlayTime(long value) {
        totalPlayTime = value;
        markDirty();
    }

    public void addPlayTime(long ticks) {
        totalPlayTime += ticks;
        markDirty();
    }

    public int getTotalGamesPlayed() {
        return totalGamesPlayed;
    }

    public void setTotalGamesPlayed(int value) {
        totalGamesPlayed = value;
        markDirty();
    }

    public void incrementTotalGamesPlayed() {
        totalGamesPlayed++;
        markDirty();
    }

    public int getTotalKills() {
        return totalKills;
    }

    public void setTotalKills(int value) {
        totalKills = value;
        markDirty();
    }

    public void incrementTotalKills() {
        totalKills++;
        markDirty();
    }

    public int getTotalDeaths() {
        return totalDeaths;
    }

    public void setTotalDeaths(int value) {
        totalDeaths = value;
        markDirty();
    }

    public void incrementTotalDeaths() {
        totalDeaths++;
        markDirty();
    }

    public int getTotalWins() {
        return totalWins;
    }

    public void setTotalWins(int value) {
        totalWins = value;
        markDirty();
    }

    public void incrementTotalWins() {
        totalWins++;
        markDirty();
    }

    public int getTotalLosses() {
        return totalLosses;
    }

    public void setTotalLosses(int value) {
        totalLosses = value;
        markDirty();
    }

    public void incrementTotalLosses() {
        totalLosses++;
        markDirty();
    }

    public int getTotalTeamKills() {
        return totalTeamKills;
    }

    public void setTotalTeamKills(int value) {
        totalTeamKills = value;
        markDirty();
    }

    public void incrementTotalTeamKills() {
        totalTeamKills++;
        markDirty();
    }

    public int getTotalLoversWins() {
        return totalLoversWins;
    }

    public void setTotalLoversWins(int value) {
        totalLoversWins = value;
        markDirty();
    }

    public void incrementTotalLoversWins() {
        totalLoversWins++;
        markDirty();
    }

    public int getTotalCivilianGames() {
        return totalCivilianGames;
    }

    public void incrementTotalCivilianGames() {
        totalCivilianGames++;
        markDirty();
    }

    public int getTotalCivilianWins() {
        return totalCivilianWins;
    }

    public void incrementTotalCivilianWins() {
        totalCivilianWins++;
        markDirty();
    }

    public int getTotalCivilianKills() {
        return totalCivilianKills;
    }

    public void incrementTotalCivilianKills() {
        totalCivilianKills++;
        markDirty();
    }

    public int getTotalCivilianDeaths() {
        return totalCivilianDeaths;
    }

    public void incrementTotalCivilianDeaths() {
        totalCivilianDeaths++;
        markDirty();
    }

    public int getTotalKillerGames() {
        return totalKillerGames;
    }

    public void incrementTotalKillerGames() {
        totalKillerGames++;
        markDirty();
    }

    public int getTotalKillerWins() {
        return totalKillerWins;
    }

    public void incrementTotalKillerWins() {
        totalKillerWins++;
        markDirty();
    }

    public int getTotalKillerKills() {
        return totalKillerKills;
    }

    public void incrementTotalKillerKills() {
        totalKillerKills++;
        markDirty();
    }

    public int getTotalKillerDeaths() {
        return totalKillerDeaths;
    }

    public void incrementTotalKillerDeaths() {
        totalKillerDeaths++;
        markDirty();
    }

    public int getTotalNeutralGames() {
        return totalNeutralGames;
    }

    public void incrementTotalNeutralGames() {
        totalNeutralGames++;
        markDirty();
    }

    public int getTotalNeutralWins() {
        return totalNeutralWins;
    }

    public void incrementTotalNeutralWins() {
        totalNeutralWins++;
        markDirty();
    }

    public int getTotalNeutralKills() {
        return totalNeutralKills;
    }

    public void incrementTotalNeutralKills() {
        totalNeutralKills++;
        markDirty();
    }

    public int getTotalNeutralDeaths() {
        return totalNeutralDeaths;
    }

    public void incrementTotalNeutralDeaths() {
        totalNeutralDeaths++;
        markDirty();
    }

    public int getTotalSheriffGames() {
        return totalSheriffGames;
    }

    public void incrementTotalSheriffGames() {
        totalSheriffGames++;
        markDirty();
    }

    public int getTotalSheriffWins() {
        return totalSheriffWins;
    }

    public void incrementTotalSheriffWins() {
        totalSheriffWins++;
        markDirty();
    }

    public int getTotalSheriffKills() {
        return totalSheriffKills;
    }

    public void incrementTotalSheriffKills() {
        totalSheriffKills++;
        markDirty();
    }

    public int getTotalSheriffDeaths() {
        return totalSheriffDeaths;
    }

    public void incrementTotalSheriffDeaths() {
        totalSheriffDeaths++;
        markDirty();
    }

    public Map<ResourceLocation, RoleStats> getRoleStats() {
        return roleStats;
    }

    public RoleStats getOrCreateRoleStats(ResourceLocation roleId) {
        return roleStats.computeIfAbsent(roleId, ignored -> new RoleStats());
    }

    public final class RoleStats {
        private int timesPlayed;
        private int killsAsRole;
        private int deathsAsRole;
        private int winsAsRole;
        private int lossesAsRole;
        private int teamKillsAsRole;

        private RoleStats() {
        }

        public int getTimesPlayed() {
            return timesPlayed;
        }

        public void setTimesPlayed(int value) {
            timesPlayed = value;
            markDirty();
        }

        public void incrementTimesPlayed() {
            timesPlayed++;
            markDirty();
        }

        public int getKillsAsRole() {
            return killsAsRole;
        }

        public void setKillsAsRole(int value) {
            killsAsRole = value;
            markDirty();
        }

        public void incrementKillsAsRole() {
            killsAsRole++;
            markDirty();
        }

        public int getDeathsAsRole() {
            return deathsAsRole;
        }

        public void setDeathsAsRole(int value) {
            deathsAsRole = value;
            markDirty();
        }

        public void incrementDeathsAsRole() {
            deathsAsRole++;
            markDirty();
        }

        public int getWinsAsRole() {
            return winsAsRole;
        }

        public void setWinsAsRole(int value) {
            winsAsRole = value;
            markDirty();
        }

        public void incrementWinsAsRole() {
            winsAsRole++;
            markDirty();
        }

        public int getLossesAsRole() {
            return lossesAsRole;
        }

        public void setLossesAsRole(int value) {
            lossesAsRole = value;
            markDirty();
        }

        public void incrementLossesAsRole() {
            lossesAsRole++;
            markDirty();
        }

        public int getTeamKillsAsRole() {
            return teamKillsAsRole;
        }

        public void setTeamKillsAsRole(int value) {
            teamKillsAsRole = value;
            markDirty();
        }

        public void incrementTeamKillsAsRole() {
            teamKillsAsRole++;
            markDirty();
        }
    }
}

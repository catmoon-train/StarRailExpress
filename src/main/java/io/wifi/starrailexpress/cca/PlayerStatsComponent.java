package io.wifi.starrailexpress.cca;

import com.google.gson.JsonSyntaxException;

import io.wifi.starrailexpress.data.PlayerStatsData;
import io.wifi.starrailexpress.util.PlayerStatsSerializer;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerStatsComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<PlayerStatsComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("player_stats"), PlayerStatsComponent.class);
    private final Player player;
    private long totalPlayTime = 0;
    private int totalGamesPlayed = 0;
    private int totalKills = 0;
    private int totalDeaths = 0;
    private int totalWins = 0;
    private int totalLosses = 0;
    private int totalTeamKills = 0;
    private int totalLoversWins = 0;

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

    private final Map<ResourceLocation, RoleStats> roleStats = new HashMap<>();

    // 文件保存相关字段
    private boolean dirty = false;
    private long lastSaveTime = 0;
    private static final long SAVE_INTERVAL = 5000;
    private static final String STATS_DIR = "play_stats";

    // 网络同步优化字段
    private boolean needsSync = false;
    private long lastSyncTime = 0;
    private static final long SYNC_INTERVAL = 5000;
    private static final long MIN_SYNC_CHANGE_THRESHOLD = 30 * 20; // 30秒游戏时间变化阈值（600 ticks）

    // 变化跟踪字段（用于优化网络同步）
    private long playTimeSinceLastSync = 0;
    private int statChangesSinceLastSync = 0;

    public PlayerStatsComponent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * 重置统计数据（用于游戏结束时）
     */


    public void sync() {
        KEY.sync(this.player);
        this.needsSync = false;
        this.lastSyncTime = System.currentTimeMillis();
        // 重置变化跟踪
        this.playTimeSinceLastSync = 0;
        this.statChangesSinceLastSync = 0;
    }

    /**
     * 手动强制同步（当玩家查看统计数据时调用）
     */
    public void syncNow() {
        sync();
    }

    private void markNeedsSync() {
        this.needsSync = true;
        this.statChangesSinceLastSync++;
    }

    private void markNeedsSyncWithPlayTime(long ticks) {
        this.needsSync = true;
        this.playTimeSinceLastSync += ticks;
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider wrapperLookup) {
        totalPlayTime = tag.getLong("TotalPlayTime");
        totalGamesPlayed = tag.getInt("TotalGamesPlayed");
        totalKills = tag.getInt("TotalKills");
        totalDeaths = tag.getInt("TotalDeaths");
        totalWins = tag.getInt("TotalWins");
        totalLosses = tag.getInt("TotalLosses");
        if (tag.contains("TotalTeamKills")) {
            totalTeamKills = tag.getInt("TotalTeamKills");
        }
        if (tag.contains("TotalLoversWins")) {
            totalLoversWins = tag.getInt("TotalLoversWins");
        }

        if (tag.contains("TotalCivilianGames")) {
            totalCivilianGames = tag.getInt("TotalCivilianGames");
        }
        if (tag.contains("TotalCivilianWins")) {
            totalCivilianWins = tag.getInt("TotalCivilianWins");
        }
        if (tag.contains("TotalCivilianKills")) {
            totalCivilianKills = tag.getInt("TotalCivilianKills");
        }
        if (tag.contains("TotalCivilianDeaths")) {
            totalCivilianDeaths = tag.getInt("TotalCivilianDeaths");
        }
        if (tag.contains("TotalKillerGames")) {
            totalKillerGames = tag.getInt("TotalKillerGames");
        }
        if (tag.contains("TotalKillerWins")) {
            totalKillerWins = tag.getInt("TotalKillerWins");
        }
        if (tag.contains("TotalKillerKills")) {
            totalKillerKills = tag.getInt("TotalKillerKills");
        }
        if (tag.contains("TotalKillerDeaths")) {
            totalKillerDeaths = tag.getInt("TotalKillerDeaths");
        }
        if (tag.contains("TotalNeutralGames")) {
            totalNeutralGames = tag.getInt("TotalNeutralGames");
        }
        if (tag.contains("TotalNeutralWins")) {
            totalNeutralWins = tag.getInt("TotalNeutralWins");
        }
        if (tag.contains("TotalNeutralKills")) {
            totalNeutralKills = tag.getInt("TotalNeutralKills");
        }
        if (tag.contains("TotalNeutralDeaths")) {
            totalNeutralDeaths = tag.getInt("TotalNeutralDeaths");
        }
        if (tag.contains("TotalSheriffGames")) {
            totalSheriffGames = tag.getInt("TotalSheriffGames");
        }
        if (tag.contains("TotalSheriffWins")) {
            totalSheriffWins = tag.getInt("TotalSheriffWins");
        }
        if (tag.contains("TotalSheriffKills")) {
            totalSheriffKills = tag.getInt("TotalSheriffKills");
        }
        if (tag.contains("TotalSheriffDeaths")) {
            totalSheriffDeaths = tag.getInt("TotalSheriffDeaths");
        }

        ListTag roleStatsList = tag.getList("RoleStats", Tag.TAG_COMPOUND);
        roleStats.clear();
        for (Tag element : roleStatsList) {
            CompoundTag roleTag = (CompoundTag) element;
            ResourceLocation roleId = ResourceLocation.parse(roleTag.getString("RoleId"));
            RoleStats stats = new RoleStats();
            stats.readFromNbt(roleTag, wrapperLookup);
            roleStats.put(roleId, stats);
        }

        // 从文件加载数据（覆盖NBT数据）
        if (!player.level().isClientSide()) {
            try {
                loadFromFile();
            } catch (Exception e) {
                SRE.LOGGER.warn("Failed to load player stats from file for {}, using NBT data",
                        player.getUUID(), e);
            }
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider wrapperLookup) {
        tag.putLong("TotalPlayTime", totalPlayTime);
        tag.putInt("TotalGamesPlayed", totalGamesPlayed);
        tag.putInt("TotalKills", totalKills);
        tag.putInt("TotalDeaths", totalDeaths);
        tag.putInt("TotalWins", totalWins);
        tag.putInt("TotalLosses", totalLosses);
        tag.putInt("TotalTeamKills", totalTeamKills);
        tag.putInt("TotalLoversWins", totalLoversWins);

        // 写入阵营统计数据
        tag.putInt("TotalCivilianGames", totalCivilianGames);
        tag.putInt("TotalCivilianWins", totalCivilianWins);
        tag.putInt("TotalCivilianKills", totalCivilianKills);
        tag.putInt("TotalCivilianDeaths", totalCivilianDeaths);
        tag.putInt("TotalKillerGames", totalKillerGames);
        tag.putInt("TotalKillerWins", totalKillerWins);
        tag.putInt("TotalKillerKills", totalKillerKills);
        tag.putInt("TotalKillerDeaths", totalKillerDeaths);
        tag.putInt("TotalNeutralGames", totalNeutralGames);
        tag.putInt("TotalNeutralWins", totalNeutralWins);
        tag.putInt("TotalNeutralKills", totalNeutralKills);
        tag.putInt("TotalNeutralDeaths", totalNeutralDeaths);
        tag.putInt("TotalSheriffGames", totalSheriffGames);
        tag.putInt("TotalSheriffWins", totalSheriffWins);
        tag.putInt("TotalSheriffKills", totalSheriffKills);
        tag.putInt("TotalSheriffDeaths", totalSheriffDeaths);
    }

    /**
     * 写入完整的NBT数据（用于文件保存）
     */
    public void writeFullNbt(@NotNull CompoundTag tag, HolderLookup.Provider wrapperLookup) {
        tag.putLong("TotalPlayTime", totalPlayTime);
        tag.putInt("TotalGamesPlayed", totalGamesPlayed);
        tag.putInt("TotalKills", totalKills);
        tag.putInt("TotalDeaths", totalDeaths);
        tag.putInt("TotalWins", totalWins);
        tag.putInt("TotalLosses", totalLosses);
        tag.putInt("TotalTeamKills", totalTeamKills);
        tag.putInt("TotalLoversWins", totalLoversWins);

        tag.putInt("TotalCivilianGames", totalCivilianGames);
        tag.putInt("TotalCivilianWins", totalCivilianWins);
        tag.putInt("TotalKillerGames", totalKillerGames);
        tag.putInt("TotalKillerWins", totalKillerWins);
        tag.putInt("TotalNeutralGames", totalNeutralGames);
        tag.putInt("TotalNeutralWins", totalNeutralWins);
        tag.putInt("TotalSheriffGames", totalSheriffGames);
        tag.putInt("TotalSheriffWins", totalSheriffWins);

        ListTag roleStatsList = new ListTag();
        for (Map.Entry<ResourceLocation, RoleStats> entry : roleStats.entrySet()) {
            CompoundTag roleTag = new CompoundTag();
            roleTag.putString("RoleId", entry.getKey().toString());
            entry.getValue().writeToNbt(roleTag, wrapperLookup);
            roleStatsList.add(roleTag);
        }
        tag.put("RoleStats", roleStatsList);
    }

    // Getter 和 Setter 方法
    public long getTotalPlayTime() {
        return totalPlayTime;
    }

    public void setTotalPlayTime(long totalPlayTime) {
        this.totalPlayTime = totalPlayTime;
        this.markDirty();
    }

    public void addPlayTime(long ticks) {
        this.totalPlayTime += ticks;
        this.markDirty();
        this.markNeedsSyncWithPlayTime(ticks);
    }

    public int getTotalGamesPlayed() {
        return totalGamesPlayed;
    }

    public void setTotalGamesPlayed(int totalGamesPlayed) {
        this.totalGamesPlayed = totalGamesPlayed;
        this.markDirty();
    }

    public void incrementTotalGamesPlayed() {
        this.totalGamesPlayed++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalKills() {
        return totalKills;
    }

    public void setTotalKills(int totalKills) {
        this.totalKills = totalKills;
        this.markDirty();
    }

    public void incrementTotalKills() {
        this.totalKills++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalDeaths() {
        return totalDeaths;
    }

    public void setTotalDeaths(int totalDeaths) {
        this.totalDeaths = totalDeaths;
        this.markDirty();
    }

    public void incrementTotalDeaths() {
        this.totalDeaths++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalWins() {
        return totalWins;
    }

    public void setTotalWins(int totalWins) {
        this.totalWins = totalWins;
        this.markDirty();
    }

    public void incrementTotalWins() {
        this.totalWins++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalLosses() {
        return totalLosses;
    }

    public void setTotalLosses(int totalLosses) {
        this.totalLosses = totalLosses;
        this.markDirty();
    }

    public void incrementTotalLosses() {
        this.totalLosses++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalTeamKills() {
        return totalTeamKills;
    }

    public void setTotalTeamKills(int totalTeamKills) {
        this.totalTeamKills = totalTeamKills;
        this.markDirty();
    }

    public void incrementTotalTeamKills() {
        this.totalTeamKills++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalLoversWins() {
        return totalLoversWins;
    }

    public void setTotalLoversWins(int totalLoversWins) {
        this.totalLoversWins = totalLoversWins;
        this.markDirty();
    }

    public void incrementTotalLoversWins() {
        this.totalLoversWins++;
        this.markDirty();
        this.markNeedsSync();
    }

    // 阵营统计 Getter 和 Setter 方法
    public int getTotalCivilianGames() {
        return totalCivilianGames;
    }

    public void incrementTotalCivilianGames() {
        this.totalCivilianGames++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalCivilianWins() {
        return totalCivilianWins;
    }

    public void incrementTotalCivilianWins() {
        this.totalCivilianWins++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalCivilianKills() {
        return totalCivilianKills;
    }

    public void incrementTotalCivilianKills() {
        this.totalCivilianKills++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalCivilianDeaths() {
        return totalCivilianDeaths;
    }

    public void incrementTotalCivilianDeaths() {
        this.totalCivilianDeaths++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalKillerGames() {
        return totalKillerGames;
    }

    public void incrementTotalKillerGames() {
        this.totalKillerGames++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalKillerWins() {
        return totalKillerWins;
    }

    public void incrementTotalKillerWins() {
        this.totalKillerWins++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalKillerKills() {
        return totalKillerKills;
    }

    public void incrementTotalKillerKills() {
        this.totalKillerKills++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalKillerDeaths() {
        return totalKillerDeaths;
    }

    public void incrementTotalKillerDeaths() {
        this.totalKillerDeaths++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalNeutralGames() {
        return totalNeutralGames;
    }

    public void incrementTotalNeutralGames() {
        this.totalNeutralGames++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalNeutralWins() {
        return totalNeutralWins;
    }

    public void incrementTotalNeutralWins() {
        this.totalNeutralWins++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalNeutralKills() {
        return totalNeutralKills;
    }

    public void incrementTotalNeutralKills() {
        this.totalNeutralKills++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalNeutralDeaths() {
        return totalNeutralDeaths;
    }

    public void incrementTotalNeutralDeaths() {
        this.totalNeutralDeaths++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalSheriffGames() {
        return totalSheriffGames;
    }

    public void incrementTotalSheriffGames() {
        this.totalSheriffGames++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalSheriffWins() {
        return totalSheriffWins;
    }

    public void incrementTotalSheriffWins() {
        this.totalSheriffWins++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalSheriffKills() {
        return totalSheriffKills;
    }

    public void incrementTotalSheriffKills() {
        this.totalSheriffKills++;
        this.markDirty();
        this.markNeedsSync();
    }

    public int getTotalSheriffDeaths() {
        return totalSheriffDeaths;
    }

    public void incrementTotalSheriffDeaths() {
        this.totalSheriffDeaths++;
        this.markDirty();
        this.markNeedsSync();
    }

    public Map<ResourceLocation, RoleStats> getRoleStats() {
        return roleStats;
    }

    public RoleStats getOrCreateRoleStats(ResourceLocation roleId) {
        return roleStats.computeIfAbsent(roleId, k -> new RoleStats());
    }

    /**
     * 检查是否需要立即同步（用于重要事件，如游戏结束）
     */
    public boolean shouldSyncImmediately() {
        return needsSync && (statChangesSinceLastSync >= 1 || playTimeSinceLastSync > MIN_SYNC_CHANGE_THRESHOLD / 2);
    }

    /**
     * 获取网络同步优化统计信息（用于调试）
     */
    public String getSyncDebugInfo() {
        return String.format("NeedsSync: %b, LastSync: %dms ago, PlayTimeChange: %d, StatChanges: %d",
                needsSync, System.currentTimeMillis() - lastSyncTime,
                playTimeSinceLastSync, statChangesSinceLastSync);
    }

    @Override
    public void serverTick() {
        long currentTime = System.currentTimeMillis();

        // 定期检查是否需要保存
        if (dirty && currentTime - lastSaveTime > SAVE_INTERVAL) {
            saveToFileAsync();
            dirty = false;
            lastSaveTime = currentTime;
        }

        // 智能批量同步检查
        if (needsSync) {
            boolean shouldSync = false;

            // 检查时间间隔
            if (currentTime - lastSyncTime > SYNC_INTERVAL) {
                shouldSync = true;
            }
            // 检查变化阈值：如果游戏时间变化超过阈值，立即同步
            else if (playTimeSinceLastSync > MIN_SYNC_CHANGE_THRESHOLD) {
                shouldSync = true;
            }
            // 检查重要统计变化：如果有多个重要统计变化，立即同步
            else if (statChangesSinceLastSync >= 3) {
                shouldSync = true;
            }

            if (shouldSync) {
                sync();
            }
        }
    }

    // 文件操作方法
    private void markDirty() {
        this.dirty = true;
    }

    /**
     * 获取保存文件路径
     */
    private Path getSaveFilePath() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path statsDir = configDir.resolve(STATS_DIR);
        UUID uuid = player.getUUID();
        return statsDir.resolve(uuid.toString() + ".json");
    }

    /**
     * 异步保存到文件
     */
    private void saveToFileAsync() {
        if (player.level().isClientSide()) {
            return; // 只在服务器端保存
        }

        String jsonData = PlayerStatsSerializer.toJson(this);
        Path filePath = getSaveFilePath();

        Util.ioPool().execute(() -> {
            try {
                // 创建目录
                Files.createDirectories(filePath.getParent());

                // 原子写入文件
                Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
                Files.writeString(tempFile, jsonData, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                Files.move(tempFile, filePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

                SRE.LOGGER.debug("Saved player stats for {} to file", player.getUUID());
            } catch (IOException e) {
                SRE.LOGGER.error("Failed to save player stats for {}", player.getUUID(), e);
            }
        });
    }

    /**
     * 从文件加载数据
     */
    public void loadFromFile() {
        if (player.level().isClientSide()) {
            return; // 只在服务器端加载
        }

        Path filePath = getSaveFilePath();
        if (!Files.exists(filePath)) {
            SRE.LOGGER.debug("No stats file found for {}, using NBT data", player.getUUID());
            return;
        }

        try {
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            PlayerStatsData data = PlayerStatsSerializer.fromJson(json);
            applyData(data);
            SRE.LOGGER.info("Loaded player stats for {} from file", player.getUUID());
        } catch (IOException e) {
            SRE.LOGGER.error("Failed to read stats file for {}", player.getUUID(), e);
        } catch (JsonSyntaxException e) {
            SRE.LOGGER.error("Failed to parse stats file for {}", player.getUUID(), e);
        }
    }

    /**
     * 将 PlayerStatsData 应用到当前组件
     */
    private void applyData(PlayerStatsData data) {
        // 应用基础统计数据
        setTotalPlayTime(data.getTotalPlayTime());
        setTotalGamesPlayed(data.getTotalGamesPlayed());
        setTotalKills(data.getTotalKills());
        setTotalDeaths(data.getTotalDeaths());
        setTotalWins(data.getTotalWins());
        setTotalLosses(data.getTotalLosses());
        setTotalTeamKills(data.getTotalTeamKills());
        setTotalLoversWins(data.getTotalLoversWins());

        // 应用阵营统计数据
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

        // 应用角色统计数据
        data.getRoleStats().forEach((roleIdStr, roleData) -> {
            ResourceLocation roleId = ResourceLocation.parse(roleIdStr);
            RoleStats roleStats = getOrCreateRoleStats(roleId);
            roleStats.setTimesPlayed(roleData.getTimesPlayed());
            roleStats.setKillsAsRole(roleData.getKillsAsRole());
            roleStats.setDeathsAsRole(roleData.getDeathsAsRole());
            roleStats.setWinsAsRole(roleData.getWinsAsRole());
            roleStats.setLossesAsRole(roleData.getLossesAsRole());
            roleStats.setTeamKillsAsRole(roleData.getTeamKillsAsRole());
        });
    }

    public class RoleStats {
        private int timesPlayed = 0;
        private int killsAsRole = 0;
        private int deathsAsRole = 0;
        private int winsAsRole = 0;
        private int lossesAsRole = 0;
        private int teamKillsAsRole = 0;

        public RoleStats() {
        }

        public void readFromNbt(CompoundTag tag, HolderLookup.Provider wrapperLookup) {
            timesPlayed = tag.getInt("TimesPlayed");
            killsAsRole = tag.getInt("KillsAsRole");
            deathsAsRole = tag.getInt("DeathsAsRole");
            winsAsRole = tag.getInt("WinsAsRole");
            lossesAsRole = tag.getInt("LossesAsRole");
            if (tag.contains("TeamKillsAsRole")) {
                teamKillsAsRole = tag.getInt("TeamKillsAsRole");
            }
        }

        public void writeToNbt(CompoundTag tag, HolderLookup.Provider wrapperLookup) {
            tag.putInt("TimesPlayed", timesPlayed);
            tag.putInt("KillsAsRole", killsAsRole);
            tag.putInt("DeathsAsRole", deathsAsRole);
            tag.putInt("WinsAsRole", winsAsRole);
            tag.putInt("LossesAsRole", lossesAsRole);
            tag.putInt("TeamKillsAsRole", teamKillsAsRole);
        }

        public int getTimesPlayed() {
            return timesPlayed;
        }

        public void setTimesPlayed(int timesPlayed) {
            this.timesPlayed = timesPlayed;
            PlayerStatsComponent.this.markDirty();
        }

        public void incrementTimesPlayed() {
            this.timesPlayed++;
            PlayerStatsComponent.this.markDirty();
            PlayerStatsComponent.this.markNeedsSync();
        }

        public int getKillsAsRole() {
            return killsAsRole;
        }

        public void setKillsAsRole(int killsAsRole) {
            this.killsAsRole = killsAsRole;
            PlayerStatsComponent.this.markDirty();
        }

        public void incrementKillsAsRole() {
            this.killsAsRole++;
            PlayerStatsComponent.this.markDirty();
            PlayerStatsComponent.this.markNeedsSync();
        }

        public int getDeathsAsRole() {
            return deathsAsRole;
        }

        public void setDeathsAsRole(int deathsAsRole) {
            this.deathsAsRole = deathsAsRole;
            PlayerStatsComponent.this.markDirty();
        }

        public void incrementDeathsAsRole() {
            this.deathsAsRole++;
            PlayerStatsComponent.this.markDirty();
            PlayerStatsComponent.this.markNeedsSync();
        }

        public int getWinsAsRole() {
            return winsAsRole;
        }

        public void setWinsAsRole(int winsAsRole) {
            this.winsAsRole = winsAsRole;
            PlayerStatsComponent.this.markDirty();
        }

        public void incrementWinsAsRole() {
            this.winsAsRole++;
            PlayerStatsComponent.this.markDirty();
            PlayerStatsComponent.this.markNeedsSync();
        }

        public int getLossesAsRole() {
            return lossesAsRole;
        }

        public void setLossesAsRole(int lossesAsRole) {
            this.lossesAsRole = lossesAsRole;
            PlayerStatsComponent.this.markDirty();
        }

        public void incrementLossesAsRole() {
            this.lossesAsRole++;
            PlayerStatsComponent.this.markDirty();
            PlayerStatsComponent.this.markNeedsSync();
        }

        public int getTeamKillsAsRole() {
            return teamKillsAsRole;
        }

        public void setTeamKillsAsRole(int teamKillsAsRole) {
            this.teamKillsAsRole = teamKillsAsRole;
            PlayerStatsComponent.this.markDirty();
        }

        public void incrementTeamKillsAsRole() {
            this.teamKillsAsRole++;
            PlayerStatsComponent.this.markDirty();
            PlayerStatsComponent.this.markNeedsSync();
        }
    }
}
package io.wifi.starrailexpress.cca;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.util.SkinManager;
import io.wifi.syncrequests.SyncRequests;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class SREPlayerProgressionComponent implements AutoSyncedComponent, ServerTickingComponent {
    private static final Logger logger = LoggerFactory.getLogger(SREPlayerProgressionComponent.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    public static final ComponentKey<SREPlayerProgressionComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("player_progression"),
            SREPlayerProgressionComponent.class);

    private static final String NETWORK_DATA_KEY = "progression";
    private static final String NETWORK_TASKS_KEY = "progression_tasks";
        private static final String LOCAL_TASK_PRESET_RESOURCE = "data/starrailexpress/progression_tasks_preset.json";
    private static final long DAILY_REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final long WEEKLY_REFRESH_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final float CARD_PREFERRED_PICK_CHANCE = 0.72F;
    private static final int DEFAULT_LEVEL_REWARD_COINS = 20;
    private static final int SYNC_DIRTY_PROGRESS = 1;
    private static final int SYNC_DIRTY_CARDS = 1 << 1;
    private static final int SYNC_DIRTY_TASKS = 1 << 2;
    private static final int SYNC_DIRTY_ALL = SYNC_DIRTY_PROGRESS | SYNC_DIRTY_CARDS | SYNC_DIRTY_TASKS;
            private static final Map<String, String> PROGRESS_SYNC_KEYS = Map.ofEntries(
                Map.entry("level", "lv"),
                Map.entry("experience", "xp"),
                Map.entry("totalExperience", "txp"),
                Map.entry("claimedCoinRewards", "ccr"),
                Map.entry("claimedLootRewards", "clr"),
                Map.entry("activeFactionCard", "afc"),
                Map.entry("factionCards", "fc"),
                Map.entry("lastQuestRefreshTime", "lqrt"),
                Map.entry("lastWeeklyRefreshTime", "lwrt"),
                Map.entry("compactTasks", "ct"),
                Map.entry("version", "v"));
            private static final Map<String, String> TASK_SYNC_KEYS = Map.ofEntries(
                Map.entry("mapping", "m"),
                Map.entry("definitions", "d"),
                Map.entry("refreshAt", "r"));
            private static final Map<String, String> QUEST_DEF_KEYS = Map.ofEntries(
                Map.entry("id", "i"),
                Map.entry("title", "t"),
                Map.entry("description", "ds"),
                Map.entry("objectiveType", "ot"),
                Map.entry("objectiveKey", "ok"),
                Map.entry("target", "tg"),
                Map.entry("rewardExperience", "rx"),
                Map.entry("rewardCoins", "rc"),
                Map.entry("rewardLoot", "rl"),
                Map.entry("rewardCard", "rd"));
        private static final Path LOCAL_TASK_CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("sre")
            .resolve("progression_tasks.json");

        private static long cachedTaskFileModified = Long.MIN_VALUE;
        private static QuestTemplatePools cachedTaskPools = new QuestTemplatePools(
            QuestTemplate.DEFAULT_DAILY_POOL,
            QuestTemplate.DEFAULT_WEEKLY_POOL);

    public static SyncRequests syncRequests = null;

    private final Player player;
    private final EnumMap<FactionCardType, Integer> factionCards = new EnumMap<>(FactionCardType.class);
    private final List<PassQuest> activeQuests = new ArrayList<>();
    private final Set<String> rewardedLevelMilestones = new HashSet<>();

    private boolean networkSyncEnabled = false;
    private long lastQuestRefreshTime;
    private long lastWeeklyRefreshTime;
    private long lastRefreshCheckGameTime;
    private int level;
    private int experience;
    private int totalExperience;
    private int claimedCoinRewards;
    private int claimedLootRewards;
    private FactionCardType activeFactionCard;
    private int syncDirtyMask = SYNC_DIRTY_ALL;

    public SREPlayerProgressionComponent(Player player) {
        this.player = player;
        this.activeFactionCard = FactionCardType.NONE;
        this.level = 1;
        this.lastQuestRefreshTime = 0L;
        this.lastWeeklyRefreshTime = 0L;
        this.lastRefreshCheckGameTime = 0L;
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                this.factionCards.put(type, 0);
            }
        }
    }

    public Player getPlayer() {
        return this.player;
    }

    public int getLevel() {
        return this.level;
    }

    public int getExperience() {
        return this.experience;
    }

    public int getTotalExperience() {
        return this.totalExperience;
    }

    public int getExperienceForNextLevel() {
        return 100 + Math.max(0, this.level - 1) * 35;
    }

    public int getClaimedCoinRewards() {
        return this.claimedCoinRewards;
    }

    public int getClaimedLootRewards() {
        return this.claimedLootRewards;
    }

    public long getLastQuestRefreshTime() {
        return this.lastQuestRefreshTime;
    }

    public long getLastWeeklyRefreshTime() {
        return this.lastWeeklyRefreshTime;
    }

    public List<PassQuest> getActiveQuests() {
        return Collections.unmodifiableList(this.activeQuests);
    }

    public List<PassQuest> getActiveDailyQuests() {
        return this.activeQuests.stream().filter(q -> q.category == QuestCategory.DAILY).toList();
    }

    public List<PassQuest> getActiveWeeklyQuests() {
        return this.activeQuests.stream().filter(q -> q.category == QuestCategory.WEEKLY).toList();
    }

    public Map<FactionCardType, Integer> getFactionCards() {
        return Collections.unmodifiableMap(this.factionCards);
    }

    public FactionCardType getActiveFactionCard() {
        return this.activeFactionCard;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        if (!SREConfig.instance().enableProgressionSystem) {
            return false;
        }
        return this.player == player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void initializeNetworkSync(String host, int port, String key) {
        if (syncRequests == null) {
            try {
                syncRequests = new SyncRequests("http://" + host + ":" + port, key);
                this.networkSyncEnabled = true;
            } catch (Exception exception) {
                logger.error("初始化玩家 {} 的进度同步失败", this.player.getName().getString(), exception);
                this.networkSyncEnabled = false;
            }
        } else {
            this.networkSyncEnabled = true;
        }
    }

    public void disableNetworkSync() {
        this.networkSyncEnabled = false;
    }

    public boolean isNetworkSyncEnabled() {
        return this.networkSyncEnabled;
    }

    public boolean prefersRoleType(int roleType) {
        return this.activeFactionCard.matchesRoleType(roleType);
    }

    public static float getCardPreferredPickChance() {
        return CARD_PREFERRED_PICK_CHANCE;
    }

    public void addFactionCard(FactionCardType type, int count) {
        if (type == FactionCardType.NONE || count == 0) {
            return;
        }
        this.factionCards.put(type, Math.max(0, this.factionCards.getOrDefault(type, 0) + count));
        markChanged(SYNC_DIRTY_CARDS);
    }

    public boolean activateFactionCard(FactionCardType type) {
        if (type == FactionCardType.NONE) {
            this.activeFactionCard = FactionCardType.NONE;
            markChanged(SYNC_DIRTY_CARDS);
            return true;
        }
        if (this.factionCards.getOrDefault(type, 0) <= 0) {
            return false;
        }
        this.activeFactionCard = type;
        markChanged(SYNC_DIRTY_CARDS);
        return true;
    }

    public void onRoleAssigned(SRERole role) {
        if (role == null || !(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        FactionCardType matchedCard = FactionCardType.fromRole(role);
        if (matchedCard != FactionCardType.NONE) {
            incrementQuest(ObjectiveType.BECOME_FACTION, matchedCard.questKey, 1);
            if (this.activeFactionCard == matchedCard) {
                int current = this.factionCards.getOrDefault(matchedCard, 0);
                if (current > 0) {
                    this.factionCards.put(matchedCard, current - 1);
                }
                this.activeFactionCard = FactionCardType.NONE;
                serverPlayer.sendSystemMessage(Component.translatable("message.sre.progression.faction_card_activated", matchedCard.displayName));
                markChanged(SYNC_DIRTY_CARDS);
            }
        }
    }

    public void onRoundQuestFinished(String questName) {
        incrementQuest(ObjectiveType.COMPLETE_ROUND_QUEST, null, 1);
        grantExperience(20, "完成列车任务");
        maybeGrantQuestRewards();
        if (questName != null && !questName.isBlank()) {
            incrementQuest(ObjectiveType.COMPLETE_SPECIFIC_QUEST, questName, 1);
        }
    }

    public void onPlayerKill() {
        incrementQuest(ObjectiveType.KILL_PLAYER, null, 1);
        grantExperience(15, "成功击杀");
        maybeGrantQuestRewards();
    }
    public void onPlayerKillDifferentTeam() {
        incrementQuest(ObjectiveType.KILL_PLAYER_DIFFERENT_TEAM, null, 1);
        grantExperience(50, "成功击杀不同阵营的玩家");
        maybeGrantQuestRewards();
    }

    public void onRoundSettled(SRERole role, boolean isWinner) {
        incrementQuest(ObjectiveType.PLAY_MATCH, null, 1);
        grantExperience(25, "完成一局游戏");
        if (isWinner) {
            incrementQuest(ObjectiveType.WIN_MATCH, null, 1);
            grantExperience(60, "获得胜利");
        }
        if (role != null) {
            FactionCardType matchedCard = FactionCardType.fromRole(role);
            if (matchedCard != FactionCardType.NONE) {
                incrementQuest(ObjectiveType.PLAY_AS_FACTION, matchedCard.questKey, 1);
                if (isWinner) {
                    incrementQuest(ObjectiveType.WIN_AS_FACTION, matchedCard.questKey, 1);
                }
            }
        }
        if (this.player.isAlive()) {
            incrementQuest(ObjectiveType.SURVIVE_MATCH, null, 1);
        }
        maybeGrantQuestRewards();
    }

    /** 管理员或自动刷新每日任务 */
    public void forceRefreshTasks() {
        if (!tryPullTasksFromNetwork()) {
            generateLocalDailyTasks(true);
        }
    }

    /** 管理员或自动刷新周常任务 */
    public void forceRefreshWeeklyTasks() {
        generateLocalWeeklyTasks(true);
    }

    @Override
    public void serverTick() {
        if (!SREConfig.instance().enableProgressionSystem) {
            return;
        }
        if (!(this.player instanceof ServerPlayer serverPlayer) || serverPlayer.serverLevel().getGameTime() % 20L != 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        // 每日任务自动刷新
        if (getActiveDailyQuests().isEmpty() || now - this.lastQuestRefreshTime >= DAILY_REFRESH_INTERVAL_MS) {
            forceRefreshTasks();
        }
        // 周常任务自动刷新
        if (SREConfig.instance().enableWeeklyTasks
                && (getActiveWeeklyQuests().isEmpty() || now - this.lastWeeklyRefreshTime >= WEEKLY_REFRESH_INTERVAL_MS)) {
            forceRefreshWeeklyTasks();
        }
        if (SREConfig.instance().progressionSyncServerEnabled && this.networkSyncEnabled
                && serverPlayer.serverLevel().getGameTime() - this.lastRefreshCheckGameTime >= 600L) {
            this.lastRefreshCheckGameTime = serverPlayer.serverLevel().getGameTime();
            syncToNetwork();
        }
    }

    public boolean tryPullTasksFromNetwork() {
        if (!SREConfig.instance().progressionSyncServerEnabled || !this.networkSyncEnabled || syncRequests == null) {
            return false;
        }
        try {
            String taskJson = syncRequests.getValue(this.player.getUUID(), NETWORK_TASKS_KEY);
            if (taskJson == null || taskJson.isBlank()) {
                return false;
            }
            Map<String, Object> networkTaskMap = GSON.fromJson(taskJson, STRING_MAP_TYPE);
            if (networkTaskMap == null || networkTaskMap.isEmpty()) {
                return false;
            }
            applyTaskPayload(networkTaskMap);

            String progressJson = syncRequests.getValue(this.player.getUUID(), NETWORK_DATA_KEY);
            if (progressJson != null && !progressJson.isBlank()) {
                Map<String, Object> progressMap = GSON.fromJson(progressJson, STRING_MAP_TYPE);
                if (progressMap != null) {
                    applyNetworkProgress(progressMap);
                }
            }
            markChanged();
            return true;
        } catch (Exception exception) {
            logger.warn("拉取玩家 {} 的远端进度/任务失败，回退到本地任务。", this.player.getName().getString(), exception);
            return false;
        }
    }

    public void syncToNetwork() {
        syncToNetwork(SYNC_DIRTY_ALL);
    }

    public void syncToNetwork(int dirtyMask) {
        if (!SREConfig.instance().progressionSyncServerEnabled || !this.networkSyncEnabled || syncRequests == null) {
            return;
        }
        try {
            boolean needProgress = (dirtyMask & (SYNC_DIRTY_PROGRESS | SYNC_DIRTY_CARDS | SYNC_DIRTY_TASKS)) != 0;
            boolean needTasks = (dirtyMask & SYNC_DIRTY_TASKS) != 0;

            if (needProgress) {
                Map<String, Object> progressData = new HashMap<>();
                putMappedValue(progressData, PROGRESS_SYNC_KEYS, "level", this.level);
                putMappedValue(progressData, PROGRESS_SYNC_KEYS, "experience", this.experience);
                putMappedValue(progressData, PROGRESS_SYNC_KEYS, "totalExperience", this.totalExperience);
                putMappedValue(progressData, PROGRESS_SYNC_KEYS, "claimedCoinRewards", this.claimedCoinRewards);
                putMappedValue(progressData, PROGRESS_SYNC_KEYS, "claimedLootRewards", this.claimedLootRewards);
                putMappedValue(progressData, PROGRESS_SYNC_KEYS, "activeFactionCard", this.activeFactionCard.name());
                putMappedValue(progressData, PROGRESS_SYNC_KEYS, "factionCards", encodeFactionCards());
                putMappedValue(progressData, PROGRESS_SYNC_KEYS, "lastQuestRefreshTime", this.lastQuestRefreshTime);
                putMappedValue(progressData, PROGRESS_SYNC_KEYS, "lastWeeklyRefreshTime", this.lastWeeklyRefreshTime);
                if (needTasks) {
                    putMappedValue(progressData, PROGRESS_SYNC_KEYS, "compactTasks", encodeCompactTaskPayload(this.activeQuests));
                }
                putMappedValue(progressData, PROGRESS_SYNC_KEYS, "version", System.currentTimeMillis());
                syncRequests.setValue(this.player.getUUID(), NETWORK_DATA_KEY, GSON.toJson(progressData));
            }

            if (needTasks) {
                Map<String, Object> taskData = new HashMap<>();
                putMappedValue(taskData, TASK_SYNC_KEYS, "mapping", buildTaskMapping(this.activeQuests));
                putMappedValue(taskData, TASK_SYNC_KEYS, "definitions", encodeQuestDefinitions(this.activeQuests));
                putMappedValue(taskData, TASK_SYNC_KEYS, "refreshAt", this.lastQuestRefreshTime);
                syncRequests.setValue(this.player.getUUID(), NETWORK_TASKS_KEY, GSON.toJson(taskData));
            }
        } catch (Exception exception) {
            logger.warn("同步玩家 {} 的进度数据失败。", this.player.getName().getString(), exception);
        }
    }

    private void incrementQuest(ObjectiveType type, String key, int amount) {
        boolean changed = false;
        for (PassQuest quest : this.activeQuests) {
            if (quest.objectiveType != type || !Objects.equals(quest.objectiveKey, key) || quest.progress >= quest.target) {
                continue;
            }
            quest.progress = Math.min(quest.target, quest.progress + amount);
            changed = true;
        }
        if (changed) {
            markChanged(SYNC_DIRTY_TASKS);
        }
    }

    private void maybeGrantQuestRewards() {
        boolean changed = false;
        for (PassQuest quest : this.activeQuests) {
            if (!quest.rewarded && quest.progress >= quest.target) {
                quest.rewarded = true;
                grantExperience(quest.rewardExperience, quest.title);
                if (quest.rewardCoins > 0) {
                    SkinManager.addCoinNum(this.player, quest.rewardCoins);
                    this.claimedCoinRewards += quest.rewardCoins;
                }
                if (quest.rewardLoot > 0) {
                    SkinManager.addLootChance(this.player, quest.rewardLoot);
                    this.claimedLootRewards += quest.rewardLoot;
                }
                if (quest.rewardCard != FactionCardType.NONE) {
                    addFactionCard(quest.rewardCard, 1);
                }
                if (this.player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.translatable("message.sre.progression.quest_completed", quest.title));
                }
                changed = true;
            }
        }
        if (changed) {
            markChanged(SYNC_DIRTY_ALL);
        }
    }

    public void grantExperience(int amount, String reason) {
        if (amount <= 0) {
            return;
        }
        this.experience += amount;
        this.totalExperience += amount;
        while (this.experience >= getExperienceForNextLevel()) {
            this.experience -= getExperienceForNextLevel();
            this.level++;
            grantLevelRewards(this.level);
            if (this.player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("message.sre.progression.level_up", this.level, reason));
            }
        }
        markChanged(SYNC_DIRTY_PROGRESS);
    }

    public void setTotalExperienceValue(int totalExperience) {
        this.level = 1;
        this.experience = 0;
        this.totalExperience = 0;
        this.rewardedLevelMilestones.clear();
        grantExperience(Math.max(0, totalExperience), "控制台设定");
        markChanged(SYNC_DIRTY_PROGRESS);
    }

    private void grantLevelRewards(int level) {
        String key = "level_" + level;
        if (!this.rewardedLevelMilestones.add(key)) {
            return;
        }
        int coinReward = DEFAULT_LEVEL_REWARD_COINS + level * 2;
        SkinManager.addCoinNum(this.player, coinReward);
        this.claimedCoinRewards += coinReward;
        if (level % 5 == 0) {
            SkinManager.addLootChance(this.player, 1);
            this.claimedLootRewards += 1;
        }
        if (level == 3) {
            addFactionCard(FactionCardType.CIVILIAN, 1);
        } else if (level == 5) {
            addFactionCard(FactionCardType.KILLER, 1);
        } else if (level == 7) {
            addFactionCard(FactionCardType.NEUTRAL, 1);
        }
    }

    private void generateLocalDailyTasks(boolean forceResetProgress) {
        long now = System.currentTimeMillis();
        long refreshWindow = now / DAILY_REFRESH_INTERVAL_MS;
        long lastWindow = this.lastQuestRefreshTime / DAILY_REFRESH_INTERVAL_MS;
        if (!forceResetProgress && !getActiveDailyQuests().isEmpty() && refreshWindow == lastWindow) {
            return;
        }
        this.activeQuests.removeIf(q -> q.category == QuestCategory.DAILY);
        List<QuestTemplate> pool = new ArrayList<>(loadTaskTemplatePools().daily());
        if (pool.isEmpty()) {
            pool = new ArrayList<>(QuestTemplate.DEFAULT_DAILY_POOL);
        }
        List<QuestTemplate> unlockedPool = filterUnlockedTemplates(pool, this.level);
        if (!unlockedPool.isEmpty()) {
            pool = unlockedPool;
        }
        Random random = new Random(refreshWindow ^ this.player.getUUID().getMostSignificantBits()
                ^ this.player.getUUID().getLeastSignificantBits());
        Collections.shuffle(pool, random);
        int taskCount = Math.max(1, SREConfig.instance().dailyTaskCount);
        pool.stream()
                .sorted(Comparator.comparingInt(template -> template.priority))
                .limit(taskCount)
                .forEach(template -> this.activeQuests.add(template.instantiate()));
        this.lastQuestRefreshTime = now;
        markChanged(SYNC_DIRTY_TASKS | SYNC_DIRTY_PROGRESS);
    }

    private void generateLocalWeeklyTasks(boolean forceResetProgress) {
        if (!SREConfig.instance().enableWeeklyTasks) {
            return;
        }
        long now = System.currentTimeMillis();
        long weeklyWindow = now / WEEKLY_REFRESH_INTERVAL_MS;
        long lastWeeklyWindow = this.lastWeeklyRefreshTime / WEEKLY_REFRESH_INTERVAL_MS;
        if (!forceResetProgress && !getActiveWeeklyQuests().isEmpty() && weeklyWindow == lastWeeklyWindow) {
            return;
        }
        this.activeQuests.removeIf(q -> q.category == QuestCategory.WEEKLY);
        List<QuestTemplate> pool = new ArrayList<>(loadTaskTemplatePools().weekly());
        if (pool.isEmpty()) {
            pool = new ArrayList<>(QuestTemplate.DEFAULT_WEEKLY_POOL);
        }
        List<QuestTemplate> unlockedPool = filterUnlockedTemplates(pool, this.level);
        if (!unlockedPool.isEmpty()) {
            pool = unlockedPool;
        }
        Random random = new Random(weeklyWindow ^ this.player.getUUID().getMostSignificantBits()
                ^ this.player.getUUID().getLeastSignificantBits());
        Collections.shuffle(pool, random);
        int taskCount = Math.max(1, SREConfig.instance().weeklyTaskCount);
        pool.stream()
                .sorted(Comparator.comparingInt(template -> template.priority))
                .limit(taskCount)
                .forEach(template -> this.activeQuests.add(template.instantiate()));
        this.lastWeeklyRefreshTime = now;
        markChanged(SYNC_DIRTY_TASKS | SYNC_DIRTY_PROGRESS);
    }

    private void applyNetworkProgress(Map<String, Object> progressMap) {
        this.level = getInt(getMappedValue(progressMap, PROGRESS_SYNC_KEYS, "level"), this.level);
        this.experience = getInt(getMappedValue(progressMap, PROGRESS_SYNC_KEYS, "experience"), this.experience);
        this.totalExperience = getInt(getMappedValue(progressMap, PROGRESS_SYNC_KEYS, "totalExperience"), this.totalExperience);
        this.claimedCoinRewards = getInt(getMappedValue(progressMap, PROGRESS_SYNC_KEYS, "claimedCoinRewards"), this.claimedCoinRewards);
        this.claimedLootRewards = getInt(getMappedValue(progressMap, PROGRESS_SYNC_KEYS, "claimedLootRewards"), this.claimedLootRewards);
        this.lastQuestRefreshTime = getLong(getMappedValue(progressMap, PROGRESS_SYNC_KEYS, "lastQuestRefreshTime"), this.lastQuestRefreshTime);
        this.lastWeeklyRefreshTime = getLong(getMappedValue(progressMap, PROGRESS_SYNC_KEYS, "lastWeeklyRefreshTime"), this.lastWeeklyRefreshTime);
        Object cardName = getMappedValue(progressMap, PROGRESS_SYNC_KEYS, "activeFactionCard");
        if (cardName instanceof String stringCard) {
            this.activeFactionCard = FactionCardType.fromString(stringCard);
        }
        Object cards = getMappedValue(progressMap, PROGRESS_SYNC_KEYS, "factionCards");
        if (cards instanceof Map<?, ?> rawMap) {
            for (var entry : rawMap.entrySet()) {
                FactionCardType type = FactionCardType.fromString(String.valueOf(entry.getKey()));
                if (type != FactionCardType.NONE) {
                    this.factionCards.put(type, getInt(entry.getValue(), this.factionCards.getOrDefault(type, 0)));
                }
            }
        }
        Object compactTasks = getMappedValue(progressMap, PROGRESS_SYNC_KEYS, "compactTasks");
        if (compactTasks instanceof List<?> rawCompact && !this.activeQuests.isEmpty()) {
            applyCompactTaskPayload(rawCompact);
        }
    }

    private void applyTaskPayload(Map<String, Object> networkTaskMap) {
        Object definitions = getMappedValue(networkTaskMap, TASK_SYNC_KEYS, "definitions");
        if (!(definitions instanceof List<?> rawDefinitions) || rawDefinitions.isEmpty()) {
            return;
        }
        this.activeQuests.removeIf(q -> q.category == QuestCategory.DAILY);
        for (Object rawDefinition : rawDefinitions) {
            if (!(rawDefinition instanceof Map<?, ?> rawMap)) {
                continue;
            }
            String id = getMapString(rawMap, QUEST_DEF_KEYS, "id", "quest_unknown");
            String title = getMapString(rawMap, QUEST_DEF_KEYS, "title", id);
            String description = getMapString(rawMap, QUEST_DEF_KEYS, "description", title);
            ObjectiveType type = ObjectiveType.fromString(getMapString(rawMap, QUEST_DEF_KEYS, "objectiveType", "PLAY_MATCH"));
            String objectiveKey = normalizeNullableString(getMapValue(rawMap, QUEST_DEF_KEYS, "objectiveKey"));
            int target = getInt(getMapValue(rawMap, QUEST_DEF_KEYS, "target"), 1);
            int rewardExperience = getInt(getMapValue(rawMap, QUEST_DEF_KEYS, "rewardExperience"), 0);
            int rewardCoins = getInt(getMapValue(rawMap, QUEST_DEF_KEYS, "rewardCoins"), 0);
            int rewardLoot = getInt(getMapValue(rawMap, QUEST_DEF_KEYS, "rewardLoot"), 0);
            FactionCardType rewardCard = FactionCardType.fromString(getMapString(rawMap, QUEST_DEF_KEYS, "rewardCard", "NONE"));
            this.activeQuests.add(new PassQuest(id, title, description, type, objectiveKey, 0, target,
                    rewardExperience, rewardCoins, rewardLoot, rewardCard, false));
        }
        this.lastQuestRefreshTime = getLong(getMappedValue(networkTaskMap, TASK_SYNC_KEYS, "refreshAt"), System.currentTimeMillis());
    }

    private void applyCompactTaskPayload(List<?> compactPayload) {
        for (int index = 0; index + 3 < compactPayload.size(); index += 4) {
            int questIndex = getInt(compactPayload.get(index), -1);
            int progress = getInt(compactPayload.get(index + 1), 0);
            int target = getInt(compactPayload.get(index + 2), 1);
            int rewarded = getInt(compactPayload.get(index + 3), 0);
            if (questIndex < 0 || questIndex >= this.activeQuests.size()) {
                continue;
            }
            PassQuest quest = this.activeQuests.get(questIndex);
            quest.progress = progress;
            quest.target = Math.max(1, target);
            quest.rewarded = rewarded == 1;
        }
    }

    private List<Integer> encodeCompactTaskPayload(List<PassQuest> quests) {
        List<Integer> compact = new ArrayList<>();
        for (int index = 0; index < quests.size(); index++) {
            PassQuest quest = quests.get(index);
            compact.add(index);
            compact.add(quest.progress);
            compact.add(quest.target);
            compact.add(quest.rewarded ? 1 : 0);
        }
        return compact;
    }

    private List<Integer> buildTaskMapping(List<PassQuest> quests) {
        List<Integer> mapping = new ArrayList<>();
        for (int index = 0; index < quests.size(); index++) {
            PassQuest quest = quests.get(index);
            mapping.add(index);
            mapping.add(quest.progress);
            mapping.add(quest.target);
        }
        return mapping;
    }

    private List<Map<String, Object>> encodeQuestDefinitions(List<PassQuest> quests) {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (PassQuest quest : quests) {
            Map<String, Object> map = new HashMap<>();
            putMappedValue(map, QUEST_DEF_KEYS, "id", quest.id);
            putMappedValue(map, QUEST_DEF_KEYS, "title", quest.title);
            putMappedValue(map, QUEST_DEF_KEYS, "description", quest.description);
            putMappedValue(map, QUEST_DEF_KEYS, "objectiveType", quest.objectiveType.name());
            putMappedValue(map, QUEST_DEF_KEYS, "objectiveKey", quest.objectiveKey == null ? "" : quest.objectiveKey);
            putMappedValue(map, QUEST_DEF_KEYS, "target", quest.target);
            putMappedValue(map, QUEST_DEF_KEYS, "rewardExperience", quest.rewardExperience);
            putMappedValue(map, QUEST_DEF_KEYS, "rewardCoins", quest.rewardCoins);
            putMappedValue(map, QUEST_DEF_KEYS, "rewardLoot", quest.rewardLoot);
            putMappedValue(map, QUEST_DEF_KEYS, "rewardCard", quest.rewardCard.name());
            definitions.add(map);
        }
        return definitions;
    }

    private Map<String, Integer> encodeFactionCards() {
        Map<String, Integer> encoded = new HashMap<>();
        for (var entry : this.factionCards.entrySet()) {
            encoded.put(entry.getKey().name(), entry.getValue());
        }
        return encoded;
    }

    private void markChanged() {
        markChanged(SYNC_DIRTY_ALL);
    }

    private void markChanged(int dirtyMask) {
        this.syncDirtyMask |= dirtyMask;
        sync();
        syncToNetwork(this.syncDirtyMask);
        this.syncDirtyMask = 0;
    }

    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        int mask = this.syncDirtyMask == 0 ? SYNC_DIRTY_ALL : this.syncDirtyMask;
        tag.putInt("SyncDirtyMask", mask);

        if ((mask & SYNC_DIRTY_PROGRESS) != 0) {
            tag.putInt("Level", this.level);
            tag.putInt("Experience", this.experience);
            tag.putInt("TotalExperience", this.totalExperience);
            tag.putInt("ClaimedCoinRewards", this.claimedCoinRewards);
            tag.putInt("ClaimedLootRewards", this.claimedLootRewards);
            tag.putLong("LastQuestRefreshTime", this.lastQuestRefreshTime);
            tag.putLong("LastWeeklyRefreshTime", this.lastWeeklyRefreshTime);
        }

        if ((mask & SYNC_DIRTY_CARDS) != 0) {
            tag.putString("ActiveFactionCard", this.activeFactionCard.name());
            CompoundTag cardTag = new CompoundTag();
            for (var entry : this.factionCards.entrySet()) {
                cardTag.putInt(entry.getKey().name(), entry.getValue());
            }
            tag.put("FactionCards", cardTag);
        }

        if ((mask & SYNC_DIRTY_TASKS) != 0) {
            ListTag questTag = new ListTag();
            for (PassQuest quest : this.activeQuests) {
                questTag.add(quest.toNbt());
            }
            tag.put("ActiveQuests", questTag);
        }
    }

    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        int mask = tag.contains("SyncDirtyMask", Tag.TAG_INT) ? tag.getInt("SyncDirtyMask") : SYNC_DIRTY_ALL;

        if ((mask & SYNC_DIRTY_PROGRESS) != 0) {
            this.level = Math.max(1, tag.getInt("Level"));
            this.experience = tag.getInt("Experience");
            this.totalExperience = tag.getInt("TotalExperience");
            this.claimedCoinRewards = tag.getInt("ClaimedCoinRewards");
            this.claimedLootRewards = tag.getInt("ClaimedLootRewards");
            this.lastQuestRefreshTime = tag.getLong("LastQuestRefreshTime");
            this.lastWeeklyRefreshTime = tag.getLong("LastWeeklyRefreshTime");
        }

        if ((mask & SYNC_DIRTY_CARDS) != 0) {
            this.activeFactionCard = FactionCardType.fromString(tag.getString("ActiveFactionCard"));
            if (tag.contains("FactionCards", Tag.TAG_COMPOUND)) {
                CompoundTag cardTag = tag.getCompound("FactionCards");
                this.factionCards.clear();
                for (FactionCardType type : FactionCardType.values()) {
                    if (type != FactionCardType.NONE) {
                        this.factionCards.put(type, cardTag.getInt(type.name()));
                    }
                }
            }
        }

        if ((mask & SYNC_DIRTY_TASKS) != 0) {
            this.activeQuests.clear();
            if (tag.contains("ActiveQuests", Tag.TAG_LIST)) {
                ListTag questList = tag.getList("ActiveQuests", Tag.TAG_COMPOUND);
                for (Tag element : questList) {
                    if (element instanceof CompoundTag questTag) {
                        this.activeQuests.add(PassQuest.fromNbt(questTag));
                    }
                }
            }
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        this.level = Math.max(1, tag.getInt("Level"));
        this.experience = tag.getInt("Experience");
        this.totalExperience = tag.getInt("TotalExperience");
        this.claimedCoinRewards = tag.getInt("ClaimedCoinRewards");
        this.claimedLootRewards = tag.getInt("ClaimedLootRewards");
        this.lastQuestRefreshTime = tag.getLong("LastQuestRefreshTime");
        this.lastWeeklyRefreshTime = tag.getLong("LastWeeklyRefreshTime");
        this.activeFactionCard = FactionCardType.fromString(tag.getString("ActiveFactionCard"));

        this.factionCards.clear();
        CompoundTag cardTag = tag.getCompound("FactionCards");
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                this.factionCards.put(type, cardTag.getInt(type.name()));
            }
        }

        this.rewardedLevelMilestones.clear();
        ListTag milestoneTag = tag.getList("RewardedMilestones", Tag.TAG_STRING);
        for (Tag value : milestoneTag) {
            this.rewardedLevelMilestones.add(value.getAsString());
        }

        this.activeQuests.clear();
        ListTag questList = tag.getList("ActiveQuests", Tag.TAG_COMPOUND);
        for (Tag element : questList) {
            if (element instanceof CompoundTag questTag) {
                this.activeQuests.add(PassQuest.fromNbt(questTag));
            }
        }

        if (getActiveDailyQuests().isEmpty() && this.player instanceof ServerPlayer) {
            generateLocalDailyTasks(false);
        }
        if (SREConfig.instance().enableWeeklyTasks && getActiveWeeklyQuests().isEmpty()
                && this.player instanceof ServerPlayer) {
            generateLocalWeeklyTasks(false);
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("Level", this.level);
        tag.putInt("Experience", this.experience);
        tag.putInt("TotalExperience", this.totalExperience);
        tag.putInt("ClaimedCoinRewards", this.claimedCoinRewards);
        tag.putInt("ClaimedLootRewards", this.claimedLootRewards);
        tag.putLong("LastQuestRefreshTime", this.lastQuestRefreshTime);
        tag.putLong("LastWeeklyRefreshTime", this.lastWeeklyRefreshTime);
        tag.putString("ActiveFactionCard", this.activeFactionCard.name());

        CompoundTag cardTag = new CompoundTag();
        for (var entry : this.factionCards.entrySet()) {
            cardTag.putInt(entry.getKey().name(), entry.getValue());
        }
        tag.put("FactionCards", cardTag);

        ListTag milestoneTag = new ListTag();
        for (String milestone : this.rewardedLevelMilestones) {
            milestoneTag.add(StringTag.valueOf(milestone));
        }
        tag.put("RewardedMilestones", milestoneTag);

        ListTag questTag = new ListTag();
        for (PassQuest quest : this.activeQuests) {
            questTag.add(quest.toNbt());
        }
        tag.put("ActiveQuests", questTag);
    }

    private static int getInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static long getLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static String normalizeNullableString(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }

    private static String getMapString(Map<?, ?> map, Map<String, String> keyMap, String canonicalKey, String fallback) {
        Object value = getMapValue(map, keyMap, canonicalKey);
        return value == null ? fallback : String.valueOf(value);
    }

    private static Object getMapValue(Map<?, ?> map, Map<String, String> keyMap, String canonicalKey) {
        Object value = map.get(canonicalKey);
        if (value != null) {
            return value;
        }
        String alias = keyMap.get(canonicalKey);
        return alias == null ? null : map.get(alias);
    }

    private static Object getMappedValue(Map<String, Object> map, Map<String, String> keyMap, String canonicalKey) {
        Object value = map.get(canonicalKey);
        if (value != null) {
            return value;
        }
        String alias = keyMap.get(canonicalKey);
        return alias == null ? null : map.get(alias);
    }

    private static void putMappedValue(Map<String, Object> map, Map<String, String> keyMap, String canonicalKey,
            Object value) {
        String alias = keyMap.getOrDefault(canonicalKey, canonicalKey);
        map.put(alias, value);
    }

    private static synchronized QuestTemplatePools loadTaskTemplatePools() {
        ensureLocalTaskFileExists();
        long modified = readLastModifiedOrDefault(LOCAL_TASK_CONFIG_PATH, Long.MIN_VALUE + 1);
        if (modified == cachedTaskFileModified) {
            return cachedTaskPools;
        }

        try (Reader reader = Files.newBufferedReader(LOCAL_TASK_CONFIG_PATH, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                throw new IllegalStateException("任务模板 JSON 根节点不是对象");
            }
            JsonObject json = root.getAsJsonObject();
            JsonArray daily = json.has("daily") && json.get("daily").isJsonArray()
                    ? json.getAsJsonArray("daily")
                    : new JsonArray();
            JsonArray weekly = json.has("weekly") && json.get("weekly").isJsonArray()
                    ? json.getAsJsonArray("weekly")
                    : new JsonArray();

            List<QuestTemplate> dailyTemplates = parseTemplateArray(daily, QuestCategory.DAILY);
            List<QuestTemplate> weeklyTemplates = parseTemplateArray(weekly, QuestCategory.WEEKLY);
            if (dailyTemplates.isEmpty()) {
                dailyTemplates = QuestTemplate.DEFAULT_DAILY_POOL;
            }
            if (weeklyTemplates.isEmpty()) {
                weeklyTemplates = QuestTemplate.DEFAULT_WEEKLY_POOL;
            }

            cachedTaskFileModified = modified;
            cachedTaskPools = new QuestTemplatePools(dailyTemplates, weeklyTemplates);
            return cachedTaskPools;
        } catch (Exception exception) {
            logger.error("读取本地任务模板失败，回退到内置默认模板: {}",
                    LOCAL_TASK_CONFIG_PATH.toAbsolutePath(), exception);
            cachedTaskFileModified = Long.MIN_VALUE;
            cachedTaskPools = new QuestTemplatePools(
                    QuestTemplate.DEFAULT_DAILY_POOL,
                    QuestTemplate.DEFAULT_WEEKLY_POOL);
            return cachedTaskPools;
        }
    }

    private static void ensureLocalTaskFileExists() {
        if (Files.exists(LOCAL_TASK_CONFIG_PATH)) {
            return;
        }
        try {
            Files.createDirectories(Objects.requireNonNull(LOCAL_TASK_CONFIG_PATH.getParent()));
            try (InputStream input = SREPlayerProgressionComponent.class.getClassLoader()
                    .getResourceAsStream(LOCAL_TASK_PRESET_RESOURCE)) {
                if (input == null) {
                    logger.warn("未找到默认任务模板资源: {}", LOCAL_TASK_PRESET_RESOURCE);
                    return;
                }
                Files.copy(input, LOCAL_TASK_CONFIG_PATH);
                logger.info("已生成本地任务模板文件: {}", LOCAL_TASK_CONFIG_PATH.toAbsolutePath());
            }
        } catch (IOException ioException) {
            logger.error("创建本地任务模板文件失败: {}", LOCAL_TASK_CONFIG_PATH.toAbsolutePath(), ioException);
        }
    }

    private static long readLastModifiedOrDefault(Path path, long fallback) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return fallback;
        }
    }

    private static List<QuestTemplate> parseTemplateArray(JsonArray array, QuestCategory defaultCategory) {
        List<QuestTemplate> templates = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            if (json.has("enabled") && !json.get("enabled").getAsBoolean()) {
                continue;
            }
            String id = getJsonString(json, "id", "quest_unknown");
            String title = getJsonString(json, "title", id);
            String description = getJsonString(json, "description", title);
            ObjectiveType objectiveType = ObjectiveType.fromString(getJsonString(json, "objectiveType", "PLAY_MATCH"));
            String objectiveKey = normalizeNullableString(getJsonString(json, "objectiveKey", ""));
            int target = Math.max(1, getJsonInt(json, "target", 1));
            int rewardExperience = Math.max(0, getJsonInt(json, "rewardExperience", 0));
            int rewardCoins = Math.max(0, getJsonInt(json, "rewardCoins", 0));
            int rewardLoot = Math.max(0, getJsonInt(json, "rewardLoot", 0));
            int priority = Math.max(1, getJsonInt(json, "priority", 1));
                int unlockLevel = Math.max(1, getJsonInt(json, "unlockLevel", 1));
            FactionCardType rewardCard = FactionCardType.fromString(getJsonString(json, "rewardCard", "NONE"));
            QuestCategory category = QuestCategory.fromString(getJsonString(json, "category", defaultCategory.name()));

            templates.add(new QuestTemplate(
                    id,
                    title,
                    description,
                    objectiveType,
                    objectiveKey,
                    target,
                    rewardExperience,
                    rewardCoins,
                    rewardLoot,
                    rewardCard,
                    priority,
                    category,
                    unlockLevel));
        }
        return templates;
    }

    private static List<QuestTemplate> filterUnlockedTemplates(List<QuestTemplate> templates, int playerLevel) {
        List<QuestTemplate> unlocked = new ArrayList<>();
        for (QuestTemplate template : templates) {
            if (playerLevel >= template.unlockLevel()) {
                unlocked.add(template);
            }
        }
        return unlocked;
    }

    private static String getJsonString(JsonObject json, String key, String fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        return json.get(key).getAsString();
    }

    private static int getJsonInt(JsonObject json, String key, int fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private record QuestTemplatePools(List<QuestTemplate> daily, List<QuestTemplate> weekly) {
    }

    public enum FactionCardType {
        NONE("sre.pass.not_active", "none"),
        KILLER("sre.pass.faction.killer", "killer"),
        CIVILIAN("sre.pass.faction.civilian", "civilian"),
        NEUTRAL("sre.pass.faction.neutral", "neutral");

        public final String displayName;
        public final String questKey;

        FactionCardType(String displayName, String questKey) {
            this.displayName = displayName;
            this.questKey = questKey;
        }

        public boolean matchesRoleType(int roleType) {
            return switch (this) {
                case KILLER -> roleType == 4;
                case CIVILIAN -> roleType <= 1;
                case NEUTRAL -> roleType == 2 || roleType == 3;
                case NONE -> false;
            };
        }

        public static FactionCardType fromRole(SRERole role) {
            if (role == null) {
                return NONE;
            }
            if (role.canUseKiller() && !role.isInnocent()) {
                return KILLER;
            }
            if (role.isNeutrals() || (!role.isInnocent() && !role.canUseKiller())) {
                return NEUTRAL;
            }
            if (role.isInnocent() && !role.isVigilanteTeam()) {
                return CIVILIAN;
            }
            return NONE;
        }

        public static FactionCardType fromString(String raw) {
            for (FactionCardType type : values()) {
                if (type.name().equalsIgnoreCase(raw) || type.questKey.equalsIgnoreCase(raw)) {
                    return type;
                }
            }
            return NONE;
        }
    }

    public enum QuestCategory {
        DAILY, WEEKLY;

        public static QuestCategory fromString(String raw) {
            try {
                return raw == null || raw.isBlank() ? DAILY : QuestCategory.valueOf(raw.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return DAILY;
            }
        }
    }

    public enum ObjectiveType {
        PLAY_MATCH,
        WIN_MATCH,
        KILL_PLAYER,
        KILL_PLAYER_DIFFERENT_TEAM,
        COMPLETE_ROUND_QUEST,
        COMPLETE_SPECIFIC_QUEST,
        PLAY_AS_FACTION,
        BECOME_FACTION,
        /** 以特定阵营赢得一局（objectiveKey=阵营questKey） */
        WIN_AS_FACTION,
        /** 在游戏结束时存活（不被杀死） */
        SURVIVE_MATCH;

        public static ObjectiveType fromString(String raw) {
            for (ObjectiveType value : values()) {
                if (value.name().equalsIgnoreCase(raw)) {
                    return value;
                }
            }
            return PLAY_MATCH;
        }
    }

    public static class PassQuest {
        public final String id;
        public final String title;
        public final String description;
        public final ObjectiveType objectiveType;
        public final String objectiveKey;
        public int progress;
        public int target;
        public final int rewardExperience;
        public final int rewardCoins;
        public final int rewardLoot;
        public final FactionCardType rewardCard;
        public boolean rewarded;
        public final QuestCategory category;

        public PassQuest(String id, String title, String description, ObjectiveType objectiveType, String objectiveKey,
                int progress, int target, int rewardExperience, int rewardCoins, int rewardLoot,
                FactionCardType rewardCard, boolean rewarded) {
            this(id, title, description, objectiveType, objectiveKey, progress, target,
                    rewardExperience, rewardCoins, rewardLoot, rewardCard, rewarded, QuestCategory.DAILY);
        }

        public PassQuest(String id, String title, String description, ObjectiveType objectiveType, String objectiveKey,
                int progress, int target, int rewardExperience, int rewardCoins, int rewardLoot,
                FactionCardType rewardCard, boolean rewarded, QuestCategory category) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.objectiveType = objectiveType;
            this.objectiveKey = objectiveKey;
            this.progress = progress;
            this.target = target;
            this.rewardExperience = rewardExperience;
            this.rewardCoins = rewardCoins;
            this.rewardLoot = rewardLoot;
            this.rewardCard = rewardCard;
            this.rewarded = rewarded;
            this.category = category;
        }

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putString("Title", this.title);
            tag.putString("Description", this.description);
            tag.putString("ObjectiveType", this.objectiveType.name());
            tag.putString("ObjectiveKey", this.objectiveKey == null ? "" : this.objectiveKey);
            tag.putInt("Progress", this.progress);
            tag.putInt("Target", this.target);
            tag.putInt("RewardExperience", this.rewardExperience);
            tag.putInt("RewardCoins", this.rewardCoins);
            tag.putInt("RewardLoot", this.rewardLoot);
            tag.putString("RewardCard", this.rewardCard.name());
            tag.putBoolean("Rewarded", this.rewarded);
            tag.putString("Category", this.category.name());
            return tag;
        }

        public static PassQuest fromNbt(CompoundTag tag) {
            return new PassQuest(
                    tag.getString("Id"),
                    tag.getString("Title"),
                    tag.getString("Description"),
                    ObjectiveType.fromString(tag.getString("ObjectiveType")),
                    normalizeNullableString(tag.getString("ObjectiveKey")),
                    tag.getInt("Progress"),
                    Math.max(1, tag.getInt("Target")),
                    tag.getInt("RewardExperience"),
                    tag.getInt("RewardCoins"),
                    tag.getInt("RewardLoot"),
                    FactionCardType.fromString(tag.getString("RewardCard")),
                    tag.getBoolean("Rewarded"),
                    QuestCategory.fromString(tag.getString("Category")));
        }
    }

    private record QuestTemplate(
            String id,
            String title,
            String description,
            ObjectiveType objectiveType,
            String objectiveKey,
            int target,
            int rewardExperience,
            int rewardCoins,
            int rewardLoot,
            FactionCardType rewardCard,
            int priority,
            QuestCategory category,
            int unlockLevel) {

        private QuestTemplate(
                String id,
                String title,
                String description,
                ObjectiveType objectiveType,
                String objectiveKey,
                int target,
                int rewardExperience,
                int rewardCoins,
                int rewardLoot,
                FactionCardType rewardCard,
                int priority,
                QuestCategory category) {
            this(id, title, description, objectiveType, objectiveKey, target, rewardExperience,
                    rewardCoins, rewardLoot, rewardCard, priority, category, 1);
        }

        // ========== 内置每日回退模板（当本地 JSON 不可用时使用）==========
        private static final List<QuestTemplate> DEFAULT_DAILY_POOL = List.of(
                new QuestTemplate("play_match", "值乘签到", "完成 1 局游戏",
                        ObjectiveType.PLAY_MATCH, null, 1, 60, 35, 0, FactionCardType.NONE, 1, QuestCategory.DAILY),
                new QuestTemplate("play_match_2", "双轨见闻", "完成 2 局游戏",
                        ObjectiveType.PLAY_MATCH, null, 2, 110, 55, 0, FactionCardType.NONE, 2, QuestCategory.DAILY),
                new QuestTemplate("win_match", "列车头号乘客", "赢下 1 局游戏",
                        ObjectiveType.WIN_MATCH, null, 1, 120, 60, 1, FactionCardType.NONE, 3, QuestCategory.DAILY),
                new QuestTemplate("win_match_2", "胜负手", "赢下 2 局游戏",
                        ObjectiveType.WIN_MATCH, null, 2, 220, 100, 1, FactionCardType.NONE, 4, QuestCategory.DAILY),
                new QuestTemplate("kill_player", "致命时刻", "击杀 1 名玩家",
                        ObjectiveType.KILL_PLAYER, null, 1, 90, 45, 0, FactionCardType.NONE, 5, QuestCategory.DAILY),
                new QuestTemplate("kill_player_2", "双重打击", "击杀 2 名玩家",
                        ObjectiveType.KILL_PLAYER, null, 2, 160, 80, 0, FactionCardType.NONE, 6, QuestCategory.DAILY),
                new QuestTemplate("kill_player_3", "三连猎手", "击杀 3 名玩家",
                        ObjectiveType.KILL_PLAYER, null, 3, 230, 110, 1, FactionCardType.NONE, 7, QuestCategory.DAILY),
                new QuestTemplate("finish_round_quest_2", "情绪管理专家", "完成 2 个局内任务",
                        ObjectiveType.COMPLETE_ROUND_QUEST, null, 2, 80, 40, 0, FactionCardType.NONE, 8, QuestCategory.DAILY),
                new QuestTemplate("finish_round_quest_3", "全力以赴", "完成 3 个局内任务",
                        ObjectiveType.COMPLETE_ROUND_QUEST, null, 3, 130, 65, 0, FactionCardType.NONE, 9, QuestCategory.DAILY),
                new QuestTemplate("survive_match", "沉默求生", "存活至游戏结束（1 局）",
                        ObjectiveType.SURVIVE_MATCH, null, 1, 100, 50, 0, FactionCardType.NONE, 10, QuestCategory.DAILY),
                new QuestTemplate("survive_match_2", "双倍谨慎", "存活至游戏结束（2 局）",
                        ObjectiveType.SURVIVE_MATCH, null, 2, 175, 85, 0, FactionCardType.NONE, 11, QuestCategory.DAILY),
                new QuestTemplate("be_killer", "危险倾向", "下一局成为杀手阵营",
                        ObjectiveType.BECOME_FACTION, FactionCardType.KILLER.questKey,
                        1, 110, 30, 0, FactionCardType.KILLER, 12, QuestCategory.DAILY),
                new QuestTemplate("be_civilian", "守序之心", "下一局成为平民阵营",
                        ObjectiveType.BECOME_FACTION, FactionCardType.CIVILIAN.questKey,
                        1, 110, 30, 0, FactionCardType.CIVILIAN, 13, QuestCategory.DAILY),
                new QuestTemplate("be_neutral", "灰色地带", "下一局成为中立阵营",
                        ObjectiveType.BECOME_FACTION, FactionCardType.NEUTRAL.questKey,
                        1, 120, 35, 0, FactionCardType.NEUTRAL, 14, QuestCategory.DAILY),
                new QuestTemplate("play_killer", "刀锋试炼", "完成 1 局杀手阵营对局",
                        ObjectiveType.PLAY_AS_FACTION, FactionCardType.KILLER.questKey,
                        1, 75, 50, 0, FactionCardType.NONE, 15, QuestCategory.DAILY),
                new QuestTemplate("play_civilian", "乘客本能", "完成 1 局平民阵营对局",
                        ObjectiveType.PLAY_AS_FACTION, FactionCardType.CIVILIAN.questKey,
                        1, 75, 50, 0, FactionCardType.NONE, 16, QuestCategory.DAILY),
                new QuestTemplate("play_neutral", "局外观察者", "完成 1 局中立阵营对局",
                        ObjectiveType.PLAY_AS_FACTION, FactionCardType.NEUTRAL.questKey,
                        1, 90, 55, 0, FactionCardType.NONE, 17, QuestCategory.DAILY),
                new QuestTemplate("win_as_killer", "悄无声息", "以杀手阵营赢得 1 局",
                        ObjectiveType.WIN_AS_FACTION, FactionCardType.KILLER.questKey,
                        1, 150, 70, 0, FactionCardType.NONE, 18, QuestCategory.DAILY),
                new QuestTemplate("win_as_civilian", "正义时刻", "以平民阵营赢得 1 局",
                        ObjectiveType.WIN_AS_FACTION, FactionCardType.CIVILIAN.questKey,
                        1, 140, 65, 0, FactionCardType.NONE, 19, QuestCategory.DAILY),
                new QuestTemplate("win_as_neutral", "独立意志", "以中立阵营赢得 1 局",
                        ObjectiveType.WIN_AS_FACTION, FactionCardType.NEUTRAL.questKey,
                        1, 155, 70, 0, FactionCardType.NONE, 20, QuestCategory.DAILY));

        // ========== 内置周常回退模板（当本地 JSON 不可用时使用）==========
        private static final List<QuestTemplate> DEFAULT_WEEKLY_POOL = List.of(
                new QuestTemplate("weekly_play_5", "周度列车员", "完成 5 局游戏",
                        ObjectiveType.PLAY_MATCH, null, 5, 500, 200, 1, FactionCardType.NONE, 1, QuestCategory.WEEKLY),
                new QuestTemplate("weekly_play_10", "资深旅客", "完成 10 局游戏",
                        ObjectiveType.PLAY_MATCH, null, 10, 900, 350, 2, FactionCardType.NONE, 2, QuestCategory.WEEKLY),
                new QuestTemplate("weekly_win_3", "列车周冠军", "赢下 3 局游戏",
                        ObjectiveType.WIN_MATCH, null, 3, 600, 250, 2, FactionCardType.NONE, 3, QuestCategory.WEEKLY),
                new QuestTemplate("weekly_kill_5", "周猎人", "累计击杀 5 名玩家",
                        ObjectiveType.KILL_PLAYER, null, 5, 550, 220, 1, FactionCardType.NONE, 4, QuestCategory.WEEKLY),
                new QuestTemplate("weekly_kill_10", "血腥收割", "累计击杀 10 名玩家",
                        ObjectiveType.KILL_PLAYER, null, 10, 950, 380, 2, FactionCardType.NONE, 5, QuestCategory.WEEKLY),
                new QuestTemplate("weekly_quest_8", "专注使命", "完成 8 个局内任务",
                        ObjectiveType.COMPLETE_ROUND_QUEST, null, 8, 700, 280, 2, FactionCardType.NONE, 6, QuestCategory.WEEKLY),
                new QuestTemplate("weekly_survive_4", "幸存者本能", "存活至游戏结束（4 局）",
                        ObjectiveType.SURVIVE_MATCH, null, 4, 480, 190, 1, FactionCardType.NONE, 7, QuestCategory.WEEKLY),
                new QuestTemplate("weekly_win_killer", "精英杀手", "以杀手阵营赢得 2 局",
                        ObjectiveType.WIN_AS_FACTION, FactionCardType.KILLER.questKey,
                        2, 650, 260, 1, FactionCardType.KILLER, 8, QuestCategory.WEEKLY),
                new QuestTemplate("weekly_win_civilian", "正义守卫", "以平民阵营赢得 2 局",
                        ObjectiveType.WIN_AS_FACTION, FactionCardType.CIVILIAN.questKey,
                        2, 620, 250, 1, FactionCardType.CIVILIAN, 9, QuestCategory.WEEKLY),
                new QuestTemplate("weekly_win_neutral", "影中幕后", "以中立阵营赢得 2 局",
                        ObjectiveType.WIN_AS_FACTION, FactionCardType.NEUTRAL.questKey,
                        2, 660, 260, 1, FactionCardType.NEUTRAL, 10, QuestCategory.WEEKLY));

        private PassQuest instantiate() {
            return new PassQuest(this.id, this.title, this.description, this.objectiveType, this.objectiveKey, 0,
                    this.target, this.rewardExperience, this.rewardCoins, this.rewardLoot, this.rewardCard, false,
                    this.category);
        }
    }
}
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

package io.wifi.starrailexpress.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import io.wifi.starrailexpress.progression.ProgressionState.PassQuest;
import io.wifi.starrailexpress.progression.ProgressionState.QuestCategory;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 通行证任务模板权威源：游戏库 {@code sre_player_sync_data} 的目录分区。
 * 网站管理员写入同一行，游戏端只轮询读取（空行时用内置预设插入一次）。
 */
public final class ProgressionQuestCatalog {
    public static final UUID CATALOG_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String DATA_KEY = "progression_task_catalog";
    private static final String PRESET_RESOURCE = "data/starrailexpress/progression_tasks_preset.json";
    private static final long POLL_INTERVAL_MS = 60_000L;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static volatile CatalogSnapshot snapshot = CatalogSnapshot.empty();
    private static volatile boolean loadInFlight;
    private static volatile long nextPollAt;

    private ProgressionQuestCatalog() {
    }

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> reloadAsync(true));
        ServerTickEvents.END_SERVER_TICK.register(ProgressionQuestCatalog::tick);
    }

    public static CatalogSnapshot current() {
        return snapshot;
    }

    public static List<PassQuest> rollDaily(int playerLevel, UUID playerUuid, long window, int count) {
        return roll(snapshot.daily, playerLevel, playerUuid, window ^ 0xD15L, Math.max(1, count));
    }

    public static List<PassQuest> rollWeekly(int playerLevel, UUID playerUuid, long window, int count) {
        return roll(snapshot.weekly, playerLevel, playerUuid, window ^ 0x7EEL, Math.max(1, count));
    }

    public static List<QuestTemplate> permanentTemplates() {
        return snapshot.permanent;
    }

    private static List<PassQuest> roll(List<QuestTemplate> source, int playerLevel, UUID playerUuid, long seed,
            int count) {
        List<QuestTemplate> pool = new ArrayList<>();
        for (QuestTemplate template : source) {
            if (template.enabled && playerLevel >= template.unlockLevel) {
                pool.add(template);
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(source);
        }
        if (pool.isEmpty()) {
            return List.of();
        }
        Random random = new Random(seed ^ playerUuid.getMostSignificantBits() ^ playerUuid.getLeastSignificantBits());
        Collections.shuffle(pool, random);
        return pool.stream()
                .sorted(Comparator.comparingInt(template -> template.priority))
                .limit(count)
                .map(QuestTemplate::instantiate)
                .toList();
    }

    private static void tick(MinecraftServer server) {
        if (!ProgressionDataManager.isActive()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextPollAt || loadInFlight) {
            return;
        }
        reloadAsync(false);
    }

    private static void reloadAsync(boolean seedIfMissing) {
        if (!MysqlPlayerDataStore.isAvailable() || loadInFlight) {
            return;
        }
        loadInFlight = true;
        nextPollAt = System.currentTimeMillis() + POLL_INTERVAL_MS;
        MysqlPlayerDataStore.loadBatchAsync(CATALOG_UUID, List.of(DATA_KEY))
                .whenComplete((records, throwable) -> {
                    loadInFlight = false;
                    if (throwable != null) {
                        SRE.LOGGER.warn("读取通行证任务目录失败。", throwable);
                        return;
                    }
                    MysqlPlayerDataStore.SyncRecord record = records.get(DATA_KEY);
                    if (record != null && record.payload() != null && !record.payload().isBlank()) {
                        CatalogSnapshot parsed = parse(record.payload());
                        if (parsed != null) {
                            snapshot = parsed;
                            return;
                        }
                    }
                    if (seedIfMissing) {
                        CatalogSnapshot seeded = loadPreset();
                        snapshot = seeded;
                        MysqlPlayerDataStore.saveBatchAsync(CATALOG_UUID, java.util.Map.of(DATA_KEY, seeded.toJson()),
                                System.currentTimeMillis());
                    }
                });
    }

    public static CatalogSnapshot parse(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject object = root.getAsJsonObject();
            List<QuestTemplate> daily = parseArray(object, "daily", QuestCategory.DAILY);
            List<QuestTemplate> weekly = parseArray(object, "weekly", QuestCategory.WEEKLY);
            List<QuestTemplate> permanent = parseArray(object, "permanent", QuestCategory.PERMANENT);
            if (daily.isEmpty() && weekly.isEmpty() && permanent.isEmpty()) {
                return null;
            }
            return new CatalogSnapshot(daily, weekly, permanent);
        } catch (RuntimeException exception) {
            SRE.LOGGER.warn("解析通行证任务目录失败。", exception);
            return null;
        }
    }

    private static CatalogSnapshot loadPreset() {
        try (InputStream input = ProgressionQuestCatalog.class.getClassLoader().getResourceAsStream(PRESET_RESOURCE)) {
            if (input == null) {
                return CatalogSnapshot.empty();
            }
            JsonElement root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            CatalogSnapshot parsed = parse(GSON.toJson(root));
            return parsed == null ? CatalogSnapshot.empty() : parsed;
        } catch (Exception exception) {
            SRE.LOGGER.warn("读取内置通行证任务预设失败。", exception);
            return CatalogSnapshot.empty();
        }
    }

    private static List<QuestTemplate> parseArray(JsonObject object, String key, QuestCategory category) {
        List<QuestTemplate> templates = new ArrayList<>();
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return templates;
        }
        JsonArray array = object.getAsJsonArray(key);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            if (json.has("enabled") && !json.get("enabled").getAsBoolean()) {
                continue;
            }
            String id = string(json, "id", "quest_unknown");
            templates.add(new QuestTemplate(
                    id,
                    string(json, "title", id),
                    string(json, "description", id),
                    ProgressionObjectives.normalize(string(json, "objectiveType", ProgressionObjectives.PLAY_MATCH)),
                    blankToNull(string(json, "objectiveKey", "")),
                    Math.max(1, integer(json, "target", 1)),
                    Math.max(0, integer(json, "rewardExperience", 0)),
                    Math.max(0, integer(json, "rewardCoins", 0)),
                    Math.max(0, integer(json, "rewardLoot", 0)),
                    FactionCardType.fromString(string(json, "rewardCard", "NONE")),
                    Math.max(1, integer(json, "priority", 1)),
                    QuestCategory.fromString(string(json, "category", category.name())),
                    Math.max(1, integer(json, "unlockLevel", 1)),
                    !json.has("enabled") || json.get("enabled").getAsBoolean()));
        }
        return templates;
    }

    private static String string(JsonObject json, String key, String fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        return json.get(key).getAsString();
    }

    private static int integer(JsonObject json, String key, int fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static final class QuestTemplate {
        public final String id;
        public final String title;
        public final String description;
        public final String objectiveType;
        public final String objectiveKey;
        public final int target;
        public final int rewardExperience;
        public final int rewardCoins;
        public final int rewardLoot;
        public final FactionCardType rewardCard;
        public final int priority;
        public final QuestCategory category;
        public final int unlockLevel;
        public final boolean enabled;

        public QuestTemplate(String id, String title, String description, String objectiveType, String objectiveKey,
                int target, int rewardExperience, int rewardCoins, int rewardLoot, FactionCardType rewardCard,
                int priority, QuestCategory category, int unlockLevel, boolean enabled) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.objectiveType = objectiveType;
            this.objectiveKey = objectiveKey;
            this.target = target;
            this.rewardExperience = rewardExperience;
            this.rewardCoins = rewardCoins;
            this.rewardLoot = rewardLoot;
            this.rewardCard = rewardCard == null ? FactionCardType.NONE : rewardCard;
            this.priority = priority;
            this.category = category == null ? QuestCategory.DAILY : category;
            this.unlockLevel = unlockLevel;
            this.enabled = enabled;
        }

        public PassQuest instantiate() {
            PassQuest quest = new PassQuest();
            quest.id = id;
            quest.title = title;
            quest.description = description;
            quest.objectiveType = objectiveType;
            quest.objectiveKey = objectiveKey;
            quest.progress = 0;
            quest.target = target;
            quest.rewardExperience = rewardExperience;
            quest.rewardCoins = rewardCoins;
            quest.rewardLoot = rewardLoot;
            quest.rewardCard = rewardCard;
            quest.rewarded = false;
            quest.category = category;
            return quest;
        }
    }

    public static final class CatalogSnapshot {
        public final List<QuestTemplate> daily;
        public final List<QuestTemplate> weekly;
        public final List<QuestTemplate> permanent;

        private CatalogSnapshot(List<QuestTemplate> daily, List<QuestTemplate> weekly, List<QuestTemplate> permanent) {
            this.daily = List.copyOf(daily);
            this.weekly = List.copyOf(weekly);
            this.permanent = List.copyOf(permanent);
        }

        public static CatalogSnapshot empty() {
            return new CatalogSnapshot(List.of(), List.of(), List.of());
        }

        public boolean isEmpty() {
            return daily.isEmpty() && weekly.isEmpty() && permanent.isEmpty();
        }

        public String toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("version", 3);
            object.add("objectives", GSON.toJsonTree(ProgressionObjectives.ALL));
            object.add("daily", GSON.toJsonTree(daily));
            object.add("weekly", GSON.toJsonTree(weekly));
            object.add("permanent", GSON.toJsonTree(permanent));
            return GSON.toJson(object);
        }
    }
}

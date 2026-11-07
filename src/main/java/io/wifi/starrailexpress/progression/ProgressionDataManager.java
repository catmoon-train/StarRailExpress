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
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.backpack.BackpackManager;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.data.PlayerSkinEconomyManager;
import io.wifi.starrailexpress.network.PlayerDataPartSyncPayload;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import io.wifi.starrailexpress.network.ProgressionQuestToastPayload;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import io.wifi.starrailexpress.progression.ProgressionState.PassQuest;
import io.wifi.starrailexpress.progression.ProgressionState.QuestCategory;
import net.exmo.sre.nametag.NameTagInventoryComponent;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressionDataManager {
    public static final String PART = "progression";
    public static final String TASKS_PART = "progression_tasks";
    private static final Gson GSON = new GsonBuilder().create();
    private static final long FLUSH_INTERVAL_MS = 5_000L;
    private static final long FLUSH_TIMEOUT_MS = 4_000L;
    private static final long DAILY_REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final long WEEKLY_REFRESH_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private ProgressionDataManager() {
    }

    public static void registerEvents() {
        ProgressionQuestCatalog.registerEvents();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(ProgressionDataManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(ProgressionDataManager::flushAllBlocking);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ENTRIES.clear());
        OnPlayerDeathWithKiller.FINAL_EVENT.register((player, killer, deathReason) -> {
            if (player instanceof ServerPlayer sp) {
                onPlayerDeath(sp, deathReason == null ? "" : deathReason.toString());
            }
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, serverPlayer, bound) -> {
            onChatMessage(serverPlayer);
            return true;
        });
    }

    /** 通行证任务仅在开启系统且 MySQL 可用时生效。 */
    public static boolean isActive() {
        return !io.wifi.starrailexpress.training.TrainingRules.isIsolated()
                && SREConfig.instance().enableProgressionSystem && isDatabaseEnabled();
    }

    public static ProgressionState get(ServerPlayer player) {
        return getEntry(player.getUUID()).state;
    }

    public static void onRoleAssigned(ServerPlayer player, SRERole role) {
        if (!isActive()) {
            return;
        }
        FactionCardType matchedCard = FactionCardType.fromRole(role);
        if (matchedCard != FactionCardType.NONE) {
            grantExperience(player, 5);
            increment(player, ProgressionObjectives.BECOME_FACTION, matchedCard.questKey, 1);
        }
    }

    public static void onRoleAssigned(Player player, SRERole role) {
        if (player instanceof ServerPlayer serverPlayer) {
            onRoleAssigned(serverPlayer, role);
        }
    }

    public static void onRoundQuestFinished(ServerPlayer player, String questName) {
        if (!isActive()) {
            return;
        }
        grantExperience(player, 20);
        increment(player, ProgressionObjectives.COMPLETE_ROUND_QUEST, null, 1);
        if (questName != null && !questName.isBlank()) {
            increment(player, ProgressionObjectives.COMPLETE_SPECIFIC_QUEST, questName, 1);
        }
    }

    public static void onRoundQuestFinished(Player player, String questName) {
        if (player instanceof ServerPlayer serverPlayer) {
            onRoundQuestFinished(serverPlayer, questName);
        }
    }

    public static void onPlayerKill(ServerPlayer player) {
        if (!isActive()) {
            return;
        }
        grantExperience(player, 15);
        increment(player, ProgressionObjectives.KILL_PLAYER, null, 1);
        SRERole killerRole = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
        if (killerRole != null) {
            String roleId = killerRole.identifier() == null ? null : killerRole.identifier().toString();
            if (roleId != null) {
                increment(player, ProgressionObjectives.KILL_AS_ROLE, roleId, 1);
            }
        }
        FactionCardType faction = currentFaction(player);
        if (faction != FactionCardType.NONE) {
            increment(player, ProgressionObjectives.KILL_AS_FACTION, faction.questKey, 1);
        }
    }

    /** 恋人阵营获胜时累加 LOVER_WIN 目标进度。 */
    public static void onLoversWin(ServerPlayer player) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.LOVER_WIN, null, 1);
    }

    public static void onPlayerKillDifferentTeam(ServerPlayer player) {
        if (!isActive()) {
            return;
        }
        grantExperience(player, 50);
        increment(player, ProgressionObjectives.KILL_PLAYER_DIFFERENT_TEAM, null, 1);
    }

    public static void onRoundSettled(ServerPlayer player, SRERole role, boolean isWinner) {
        if (!isActive()) {
            return;
        }
        grantExperience(player, isWinner ? 85 : 25);
        if (isWinner) {
            PlayerSkinEconomyManager.addCoinNum(player, 20);
            getEntry(player.getUUID()).state.claimedCoinRewards += 20;
        }
        increment(player, ProgressionObjectives.PLAY_MATCH, null, 1);
        if (isWinner) {
            increment(player, ProgressionObjectives.WIN_MATCH, null, 1);
        }
        String modeId = currentGameModeId(player);
        if (modeId != null) {
            increment(player, ProgressionObjectives.PLAY_GAME_MODE, modeId, 1);
            if (isWinner) {
                increment(player, ProgressionObjectives.WIN_GAME_MODE, modeId, 1);
            }
        }
        if (role != null) {
            String roleId = role.identifier() == null ? null : role.identifier().toString();
            increment(player, ProgressionObjectives.PLAY_AS_ROLE, roleId, 1);
            if (isWinner) {
                increment(player, ProgressionObjectives.WIN_AS_ROLE, roleId, 1);
            }
            FactionCardType faction = FactionCardType.fromRole(role);
            if (faction != FactionCardType.NONE) {
                increment(player, ProgressionObjectives.PLAY_AS_FACTION, faction.questKey, 1);
                if (isWinner) {
                    increment(player, ProgressionObjectives.WIN_AS_FACTION, faction.questKey, 1);
                }
                if (player.isAlive()) {
                    increment(player, ProgressionObjectives.SURVIVE_AS_FACTION, faction.questKey, 1);
                }
            }
        }
        if (player.isAlive()) {
            increment(player, ProgressionObjectives.SURVIVE_MATCH, null, 1);
        }
        onRoleAssigned(player, role);
    }

    public static void onItemUsed(ServerPlayer player, String itemId) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.USE_ITEM, itemId, 1);
    }

    public static void onPickupItem(ServerPlayer player, String itemId) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.PICKUP_ITEM, itemId, 1);
    }

    public static void onShopBuy(UUID playerUuid, String itemId) {
        ServerPlayer player = onlinePlayer(playerUuid);
        if (player != null) {
            onShopBuy(player, itemId);
        }
    }

    public static void onShopBuy(ServerPlayer player, String itemId) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.BUY_SHOP_ITEM, itemId, 1);
    }

    /** 消耗游戏内金币（多局累计）时累加 GOLD_SPENT 目标进度。 */
    public static void onGoldSpent(UUID playerUuid, int amount) {
        if (!isActive() || amount <= 0) {
            return;
        }
        ServerPlayer player = onlinePlayer(playerUuid);
        if (player != null) {
            increment(player, ProgressionObjectives.GOLD_SPENT, null, amount);
        }
    }

    /** 按货币类型累计"消耗金币/消耗游戏币"目标进度（商店/售货机/抽奖机通用）。 */
    public static void onCurrencySpent(UUID playerUuid, ShopEntry.Currency currency, int amount) {
        if (!isActive() || amount <= 0) {
            return;
        }
        ServerPlayer player = onlinePlayer(playerUuid);
        if (player != null) {
            onCurrencySpent(player, currency, amount);
        }
    }

    public static void onCurrencySpent(ServerPlayer player, ShopEntry.Currency currency, int amount) {
        if (!isActive() || amount <= 0 || currency == null) {
            return;
        }
        if (currency == ShopEntry.Currency.MONEY) {
            increment(player, ProgressionObjectives.GOLD_SPENT, null, amount);
        } else if (currency == ShopEntry.Currency.MINIGAME_TOKEN) {
            increment(player, ProgressionObjectives.GAME_TOKEN_SPENT, null, amount);
        }
    }

    /** 在售货机完成一次购买时累加 VENDING_PURCHASE 目标进度。 */
    public static void onVendingBuy(UUID playerUuid, String itemId) {
        if (!isActive()) {
            return;
        }
        ServerPlayer player = onlinePlayer(playerUuid);
        if (player != null) {
            onVendingBuy(player, itemId);
        }
    }

    public static void onVendingBuy(ServerPlayer player, String itemId) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.VENDING_PURCHASE, itemId, 1);
    }

    /** 在抽奖机完成一次抽奖时累加 LOTTERY_PURCHASE 目标进度。 */
    public static void onLotteryDraw(UUID playerUuid) {
        if (!isActive()) {
            return;
        }
        ServerPlayer player = onlinePlayer(playerUuid);
        if (player != null) {
            onLotteryDraw(player);
        }
    }

    public static void onLotteryDraw(ServerPlayer player) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.LOTTERY_PURCHASE, null, 1);
    }

    /** 中立玩家达成独立/特殊胜负时累加 NEUTRAL_INDEPENDENT_WIN 目标进度。 */
    public static void onNeutralIndependentWin(ServerPlayer player) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.NEUTRAL_INDEPENDENT_WIN, null, 1);
    }

    public static void onSkillUsed(ServerPlayer player, String skillId) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.USE_SKILL, skillId, 1);
    }

    public static void onReportBody(ServerPlayer player) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.REPORT_BODY, null, 1);
    }

    public static void onCallMeeting(ServerPlayer player) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.CALL_MEETING, null, 1);
    }

    public static void onCastVote(ServerPlayer player) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.CAST_VOTE, null, 1);
    }

    /** 完成一个局内小游戏任务时累加 MINIGAME_TASK_COMPLETE。 */
    public static void onMinigameTaskComplete(ServerPlayer player, String minigameId) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.MINIGAME_TASK_COMPLETE, minigameId, 1);
    }

    /** 恢复理智值时累加 SANITY_RESTORED（累计恢复量）。 */
    public static void onSanityRestored(ServerPlayer player, int amount) {
        if (!isActive() || amount <= 0) {
            return;
        }
        increment(player, ProgressionObjectives.SANITY_RESTORED, null, amount);
    }

    /** 消耗体力值时累加 STAMINA_CONSUMED（累计消耗量）。 */
    public static void onStaminaConsumed(ServerPlayer player, int amount) {
        if (!isActive() || amount <= 0) {
            return;
        }
        increment(player, ProgressionObjectives.STAMINA_CONSUMED, null, amount);
    }

    /** 状态条数值变化（恢复/减少）时累加 STATUS_BAR_RESTORED。 */
    public static void onStatusBarRestored(ServerPlayer player, String type, int delta) {
        if (!isActive() || delta == 0 || type == null) {
            return;
        }
        int value = type.equalsIgnoreCase("POLLUTION")
                ? (delta < 0 ? -delta : 0)
                : (delta > 0 ? delta : 0);
        if (value > 0) {
            increment(player, ProgressionObjectives.STATUS_BAR_RESTORED, type, value);
        }
    }

    /** 玩家死亡时累加 PLAYER_DEATH（可按 deathreason 区分）。 */
    public static void onPlayerDeath(ServerPlayer player, String deathReason) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.PLAYER_DEATH, deathReason, 1);
    }

    /** 玩家转职时累加 ROLE_CHANGED。 */
    public static void onRoleChanged(ServerPlayer player, String roleId) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.ROLE_CHANGED, roleId, 1);
    }

    /** 玩家被复活时累加 REVIVED。 */
    public static void onRevived(ServerPlayer player) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.REVIVED, null, 1);
    }

    /** 对其他玩家造成虚拟血量伤害时累加 VIRTUAL_DAMAGE_DEALT。 */
    public static void onVirtualDamageDealt(ServerPlayer attacker, int damage) {
        if (!isActive() || damage <= 0) {
            return;
        }
        increment(attacker, ProgressionObjectives.VIRTUAL_DAMAGE_DEALT, null, damage);
    }

    /** 获得护盾层数时累加 SHIELD_GAINED（key=护盾类型）。 */
    public static void onShieldGained(ServerPlayer player, String shieldType, int layers) {
        if (!isActive() || layers <= 0) {
            return;
        }
        increment(player, ProgressionObjectives.SHIELD_GAINED, shieldType, layers);
    }

    /** 参与会议时累加 MEETING_PARTICIPATED（key=会议类型 map/fool）。 */
    public static void onMeetingParticipated(ServerPlayer player, String meetingType) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.MEETING_PARTICIPATED, meetingType, 1);
    }

    /** 聊天栏发言时累加 CHAT_MESSAGES。 */
    public static void onChatMessage(ServerPlayer player) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.CHAT_MESSAGES, null, 1);
    }

    /** 使用滑索时累加 ZIPLINE_USED。 */
    public static void onZiplineUsed(ServerPlayer player) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.ZIPLINE_USED, null, 1);
    }

    /** 从饮水机取水时累加 WATER_DISPENSER_USED。 */
    public static void onWaterDispenserUsed(ServerPlayer player) {
        if (!isActive()) {
            return;
        }
        increment(player, ProgressionObjectives.WATER_DISPENSER_USED, null, 1);
    }

    /**
     * 游玩指定地图时累加 PLAY_SPECIFIC_MAP（key=地图 ID），
     * 并把当前周期内该地图的游玩局数记入 progression 分区（随每日任务一同刷新重置，供网站读取）。
     */
    public static void onPlayMap(ServerPlayer player, String mapId) {
        if (!isActive() || mapId == null || mapId.isBlank()) {
            return;
        }
        // 先累加任务进度：increment 内部的 ensureQuests 可能触发每日刷新并清空 mapPlayCounts，
        // 之后再记本次局数，保证刷新跨天时计数从新周期开始。
        increment(player, ProgressionObjectives.PLAY_SPECIFIC_MAP, mapId, 1);
        Entry entry = getEntry(player.getUUID());
        entry.state.mapPlayCounts.merge(mapId, 1, Integer::sum);
        markDirty(player, entry);
    }

    public static void addFactionCard(ServerPlayer player, ProgressionState.FactionCardType type, int count) {
        BackpackManager.addCard(player, type, count);
    }

    public static void addFactionCard(Player player, ProgressionState.FactionCardType type, int count) {
        if (player instanceof ServerPlayer serverPlayer) {
            addFactionCard(serverPlayer, type, count);
        }
    }

    public static boolean activateFactionCard(ServerPlayer player, ProgressionState.FactionCardType type) {
        return BackpackManager.activateCard(player, type);
    }

    public static void markFactionCardsCleared(ServerPlayer player) {
        markDirty(player, getEntry(player.getUUID()));
    }

    public static boolean isLoaded(UUID playerUuid) {
        Entry entry = ENTRIES.get(playerUuid);
        return entry != null && entry.loaded;
    }

    public static boolean flushBlocking(UUID playerUuid) {
        Entry entry = ENTRIES.get(playerUuid);
        if (entry == null || !isDatabaseEnabled()) {
            return false;
        }
        boolean success = MysqlPlayerDataStore.saveBatchBlocking(
                playerUuid,
                buildPayloads(entry),
                Math.max(1L, entry.updatedAt),
                FLUSH_TIMEOUT_MS);
        if (success) {
            entry.dirty = false;
        }
        return success;
    }

    private static void increment(ServerPlayer player, String type, String key, int amount) {
        if (amount <= 0) {
            return;
        }
        Entry entry = getEntry(player.getUUID());
        ensureQuests(player, entry);
        boolean changed = false;
        List<PassQuest> completed = new ArrayList<>();
        for (PassQuest quest : entry.state.activeQuests) {
            if (quest.rewarded || quest.progress >= Math.max(1, quest.target)) {
                continue;
            }
            String objective = ProgressionObjectives.normalize(quest.objectiveType);
            if (!objective.equals(type) || !ProgressionObjectives.keyMatches(quest.objectiveKey, key)) {
                continue;
            }
            quest.progress = Math.min(Math.max(1, quest.target), quest.progress + amount);
            changed = true;
            if (quest.progress >= quest.target) {
                completed.add(quest);
            }
        }
        if (!completed.isEmpty()) {
            grantQuestRewards(player, entry, completed);
        } else if (changed) {
            markDirty(player, entry);
        }
    }

    private static void grantQuestRewards(ServerPlayer player, Entry entry, List<PassQuest> completed) {
        for (PassQuest quest : completed) {
            if (quest.rewarded) {
                continue;
            }
            quest.rewarded = true;
            grantExperience(player, quest.rewardExperience);
            if (quest.rewardCoins > 0) {
                PlayerSkinEconomyManager.addCoinNum(player, quest.rewardCoins);
                entry.state.claimedCoinRewards += quest.rewardCoins;
            }
            if (quest.rewardLoot > 0) {
                PlayerSkinEconomyManager.addLootChance(player, quest.rewardLoot);
                entry.state.claimedLootRewards += quest.rewardLoot;
            }
            if (quest.rewardCard != null && quest.rewardCard != FactionCardType.NONE) {
                BackpackManager.addCard(player, quest.rewardCard, 1);
            }
            ServerPlayNetworking.send(player, new ProgressionQuestToastPayload(
                    quest.title == null ? "" : quest.title,
                    quest.rewardExperience,
                    quest.rewardCoins,
                    quest.rewardLoot));
        }
        markDirty(player, entry);
    }

    private static void grantExperience(ServerPlayer player, int amount) {
        if (!isActive() || amount <= 0) {
            return;
        }
        Entry entry = getEntry(player.getUUID());
        int previousLevel = entry.state.level;
        entry.state.experience += amount;
        entry.state.totalExperience += amount;
        while (entry.state.experience >= entry.state.getExperienceForNextLevel()) {
            entry.state.experience -= entry.state.getExperienceForNextLevel();
            entry.state.level++;
            int coinReward = 20 + entry.state.level * 2;
            PlayerSkinEconomyManager.addCoinNum(player, coinReward);
            entry.state.claimedCoinRewards += coinReward;
            if (entry.state.level % 5 == 0) {
                PlayerSkinEconomyManager.addLootChance(player, 1);
                entry.state.claimedLootRewards++;
            }
        }
        markDirty(player, entry);
        if (previousLevel < NameTagInventoryComponent.JUNIOR_NAME_TAG_MAX_LEVEL
                && entry.state.level >= NameTagInventoryComponent.JUNIOR_NAME_TAG_MAX_LEVEL) {
            NameTagInventoryComponent.KEY.get(player).sync();
        }
    }

    private static void ensureQuests(ServerPlayer player, Entry entry) {
        if (!isActive()) {
            return;
        }
        if (grantPendingQuestRewards(player, entry)) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = refreshDailyIfNeeded(entry, now, false);
        changed = refreshWeeklyIfNeeded(entry, now, false) || changed;
        changed = ensurePermanent(entry) || changed;
        if (changed) {
            markDirty(player, entry);
        }
    }

    private static boolean grantPendingQuestRewards(ServerPlayer player, Entry entry) {
        List<PassQuest> pending = new ArrayList<>();
        for (PassQuest quest : entry.state.activeQuests) {
            if (!quest.rewarded && quest.progress >= Math.max(1, quest.target)) {
                pending.add(quest);
            }
        }
        if (pending.isEmpty()) {
            return false;
        }
        grantQuestRewards(player, entry, pending);
        return true;
    }

    private static boolean refreshDailyIfNeeded(Entry entry, long now, boolean force) {
        if (ProgressionQuestCatalog.current().daily.isEmpty()) {
            return false;
        }
        long window = now / DAILY_REFRESH_INTERVAL_MS;
        long lastWindow = entry.state.lastQuestRefreshTime / DAILY_REFRESH_INTERVAL_MS;
        List<PassQuest> daily = entry.state.getActiveDailyQuests();
        if (!force && !daily.isEmpty() && window == lastWindow) {
            return false;
        }
        entry.state.activeQuests.removeIf(quest -> quest.category == QuestCategory.DAILY);
        entry.state.activeQuests.addAll(ProgressionQuestCatalog.rollDaily(
                entry.state.level, entry.playerUuid, window, SREConfig.instance().dailyTaskCount));
        entry.state.lastQuestRefreshTime = now;
        // 与每日任务一同重置：地图游玩局数按日归零，由网站展示当前周期数据
        entry.state.mapPlayCounts.clear();
        return true;
    }

    private static boolean refreshWeeklyIfNeeded(Entry entry, long now, boolean force) {
        if (!SREConfig.instance().enableWeeklyTasks || ProgressionQuestCatalog.current().weekly.isEmpty()) {
            return false;
        }
        long window = now / WEEKLY_REFRESH_INTERVAL_MS;
        long lastWindow = entry.state.lastWeeklyRefreshTime / WEEKLY_REFRESH_INTERVAL_MS;
        List<PassQuest> weekly = entry.state.getActiveWeeklyQuests();
        if (!force && !weekly.isEmpty() && window == lastWindow) {
            return false;
        }
        entry.state.activeQuests.removeIf(quest -> quest.category == QuestCategory.WEEKLY);
        entry.state.activeQuests.addAll(ProgressionQuestCatalog.rollWeekly(
                entry.state.level, entry.playerUuid, window, SREConfig.instance().weeklyTaskCount));
        entry.state.lastWeeklyRefreshTime = now;
        return true;
    }

    private static boolean ensurePermanent(Entry entry) {
        Set<String> existing = new HashSet<>();
        for (PassQuest quest : entry.state.activeQuests) {
            if (quest.category == QuestCategory.PERMANENT && quest.id != null) {
                existing.add(quest.id);
            }
        }
        boolean added = false;
        for (ProgressionQuestCatalog.QuestTemplate template : ProgressionQuestCatalog.permanentTemplates()) {
            if (!template.enabled || entry.state.level < template.unlockLevel || existing.contains(template.id)) {
                continue;
            }
            entry.state.activeQuests.add(template.instantiate());
            existing.add(template.id);
            added = true;
        }
        return added;
    }

    private static void onJoin(ServerPlayer player) {
        if (io.wifi.starrailexpress.training.TrainingRules.isTrainingWorld(player.level())) return;
        Entry entry = getEntry(player.getUUID());
        entry.online = true;
        send(player, entry);
        if (!isDatabaseEnabled()) {
            entry.loaded = true;
            BackpackManager.migrateIfNeeded(player);
            return;
        }
        reloadFromDatabase(player, entry);
    }

    private static void reloadFromDatabase(ServerPlayer player, Entry entry) {
        if (!isDatabaseEnabled() || entry.loadInFlight) {
            return;
        }
        entry.loadInFlight = true;
        MysqlPlayerDataStore.loadBatchAsync(player.getUUID(), List.of(PART, TASKS_PART))
                .whenComplete((records, throwable) -> {
                    entry.loadInFlight = false;
                    MinecraftServer server = player.getServer();
                    if (server == null) {
                        return;
                    }
                    server.execute(() -> {
                        if (ENTRIES.get(player.getUUID()) != entry) {
                            return;
                        }
                        if (throwable != null) {
                            SRE.LOGGER.warn("Failed to load progression part for {}", player.getUUID(), throwable);
                            return;
                        }
                        MysqlPlayerDataStore.SyncRecord progressRecord = records.get(PART);
                        if (progressRecord != null && progressRecord.payload() != null && !progressRecord.payload().isBlank()) {
                            entry.state = fromJson(progressRecord.payload());
                            entry.updatedAt = Math.max(entry.updatedAt, progressRecord.updatedAt());
                            entry.dirty = false;
                        }
                        MysqlPlayerDataStore.SyncRecord taskRecord = records.get(TASKS_PART);
                        if ((entry.state.activeQuests == null || entry.state.activeQuests.isEmpty())
                                && taskRecord != null && taskRecord.payload() != null && !taskRecord.payload().isBlank()) {
                            applyTaskDefinitions(entry.state, taskRecord.payload());
                        }
                        if (isActive()) {
                            ensureQuests(player, entry);
                        }
                        entry.loaded = true;
                        send(player, entry);
                        BackpackManager.migrateIfNeeded(player);
                        NameTagInventoryComponent.KEY.get(player).sync();
                    });
                });
    }

    private static void onDisconnect(ServerPlayer player) {
        Entry entry = ENTRIES.get(player.getUUID());
        if (entry != null) {
            flushAsync(player, entry);
            ENTRIES.remove(player.getUUID(), entry);
        }
    }

    private static void tick(MinecraftServer server) {
        if (io.wifi.starrailexpress.training.TrainingRules.isTrainingServer(server)) return;
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Entry entry = ENTRIES.get(player.getUUID());
            if (entry == null || !entry.online) {
                continue;
            }
            if (isActive() && entry.loaded && now - entry.lastQuestCheckAt >= 20_000L) {
                entry.lastQuestCheckAt = now;
                ensureQuests(player, entry);
            }
            if (!entry.dirty || entry.saveInFlight || now - entry.lastFlushAt < FLUSH_INTERVAL_MS) {
                continue;
            }
            flushAsync(player, entry);
        }
    }

    private static void flushAsync(ServerPlayer player, Entry entry) {
        if (!isDatabaseEnabled() || entry.loadInFlight) {
            return;
        }
        entry.saveInFlight = true;
        entry.dirty = false;
        entry.lastFlushAt = System.currentTimeMillis();
        long updatedAt = Math.max(1L, entry.updatedAt);
        MysqlPlayerDataStore.saveBatchAsync(player.getUUID(), buildPayloads(entry), updatedAt)
                .whenComplete((success, throwable) -> {
                    entry.saveInFlight = false;
                    if (throwable != null || !Boolean.TRUE.equals(success)) {
                        entry.dirty = true;
                        if (throwable != null) {
                            SRE.LOGGER.warn("Failed to save progression part for {}", player.getUUID(), throwable);
                        } else {
                            reloadFromDatabase(player, entry);
                        }
                    }
                });
    }

    private static void flushAllBlocking(MinecraftServer server) {
        if (!isDatabaseEnabled()) {
            return;
        }
        List<UUID> playersToFlush = server.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID)
                .filter(ENTRIES::containsKey)
                .toList();
        for (UUID uuid : playersToFlush) {
            Entry entry = ENTRIES.get(uuid);
            if (entry == null || !entry.dirty) {
                continue;
            }
            flushBlocking(uuid);
        }
    }

    private static Entry getEntry(UUID uuid) {
        return ENTRIES.computeIfAbsent(uuid, Entry::new);
    }

    private static void markDirty(ServerPlayer player, Entry entry) {
        entry.updatedAt = Math.max(System.currentTimeMillis(), entry.updatedAt + 1L);
        entry.state.version = entry.updatedAt;
        entry.dirty = true;
        send(player, entry);
    }

    private static void send(ServerPlayer player, Entry entry) {
        ServerPlayNetworking.send(player,
                new PlayerDataPartSyncPayload(player.getUUID(), PART, toClientJson(entry.state, entry.updatedAt),
                        entry.updatedAt));
    }

    private static boolean isDatabaseEnabled() {
        return !io.wifi.starrailexpress.training.TrainingRules.isIsolated()
                && SREConfig.instance().mysqlPlayerSyncEnabled && MysqlPlayerDataStore.isAvailable();
    }

    private static Map<String, String> buildPayloads(Entry entry) {
        Map<String, String> payloads = new java.util.HashMap<>();
        payloads.put(PART, toDatabaseJson(entry.state, entry.updatedAt));
        payloads.put(TASKS_PART, toTaskJson(entry.state));
        return payloads;
    }

    private static ProgressionState fromJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root != null && root.isJsonObject()) {
                JsonObject object = root.getAsJsonObject();
                if (object.has("lv") && !object.has("level")) {
                    return fromCompactProgress(object);
                }
            }
            ProgressionState state = GSON.fromJson(json, ProgressionState.class);
            return state == null ? ProgressionState.createDefault() : state.normalized();
        } catch (RuntimeException exception) {
            return ProgressionState.createDefault();
        }
    }

    private static ProgressionState fromCompactProgress(JsonObject object) {
        ProgressionState state = ProgressionState.createDefault();
        state.level = Math.max(1, intValue(object, "lv", 1));
        state.experience = intValue(object, "xp", 0);
        state.totalExperience = intValue(object, "txp", 0);
        state.claimedCoinRewards = intValue(object, "ccr", 0);
        state.claimedLootRewards = intValue(object, "clr", 0);
        state.lastQuestRefreshTime = longValue(object, "lqrt", 0L);
        state.lastWeeklyRefreshTime = longValue(object, "lwrt", 0L);
        state.version = longValue(object, "v", 0L);
        if (object.has("activeQuests") && object.get("activeQuests").isJsonArray()) {
            ProgressionState parsed = GSON.fromJson(object, ProgressionState.class);
            if (parsed != null && parsed.activeQuests != null) {
                state.activeQuests = parsed.normalized().activeQuests;
            }
        }
        return state.normalized();
    }

    private static void applyTaskDefinitions(ProgressionState state, String json) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            JsonElement definitions = object.has("d") ? object.get("d") : object.get("definitions");
            if (definitions == null || !definitions.isJsonArray()) {
                return;
            }
            JsonArray array = definitions.getAsJsonArray();
            if (array.isEmpty()) {
                return;
            }
            List<PassQuest> quests = new ArrayList<>();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject questJson = element.getAsJsonObject();
                PassQuest quest = new PassQuest();
                quest.id = stringValue(questJson, "i", stringValue(questJson, "id", "quest_unknown"));
                quest.title = stringValue(questJson, "t", stringValue(questJson, "title", quest.id));
                quest.description = stringValue(questJson, "ds", stringValue(questJson, "description", quest.title));
                quest.objectiveType = ProgressionObjectives.normalize(
                        stringValue(questJson, "ot", stringValue(questJson, "objectiveType", ProgressionObjectives.PLAY_MATCH)));
                quest.objectiveKey = blankToNull(
                        stringValue(questJson, "ok", stringValue(questJson, "objectiveKey", "")));
                quest.target = Math.max(1, intValue(questJson, "tg", intValue(questJson, "target", 1)));
                quest.rewardExperience = intValue(questJson, "rx", intValue(questJson, "rewardExperience", 0));
                quest.rewardCoins = intValue(questJson, "rc", intValue(questJson, "rewardCoins", 0));
                quest.rewardLoot = intValue(questJson, "rl", intValue(questJson, "rewardLoot", 0));
                quest.rewardCard = FactionCardType.fromString(
                        stringValue(questJson, "rd", stringValue(questJson, "rewardCard", "NONE")));
                quest.category = QuestCategory.fromString(
                        stringValue(questJson, "cg", stringValue(questJson, "category", "DAILY")));
                quests.add(quest);
            }
            JsonElement compact = object.get("m");
            applyCompactProgress(quests, compact, false);
            if (!quests.isEmpty()) {
                state.activeQuests = quests;
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static void applyCompactProgress(List<PassQuest> quests, JsonElement compact, boolean includesRewarded) {
        if (compact == null || !compact.isJsonArray()) {
            return;
        }
        JsonArray values = compact.getAsJsonArray();
        int stride = includesRewarded ? 4 : 3;
        for (int index = 0; index + stride - 1 < values.size(); index += stride) {
            int questIndex = values.get(index).getAsInt();
            if (questIndex < 0 || questIndex >= quests.size()) {
                continue;
            }
            PassQuest quest = quests.get(questIndex);
            quest.progress = values.get(index + 1).getAsInt();
            quest.target = Math.max(1, values.get(index + 2).getAsInt());
            if (includesRewarded) {
                quest.rewarded = values.get(index + 3).getAsInt() == 1;
            }
        }
    }

    private static String toClientJson(ProgressionState state, long updatedAt) {
        state.version = updatedAt;
        return GSON.toJson(state.normalized());
    }

    private static String toDatabaseJson(ProgressionState state, long updatedAt) {
        state.version = updatedAt;
        JsonObject object = GSON.toJsonTree(state.normalized()).getAsJsonObject();
        object.addProperty("lv", state.level);
        object.addProperty("xp", state.experience);
        object.addProperty("txp", state.totalExperience);
        object.addProperty("ccr", state.claimedCoinRewards);
        object.addProperty("clr", state.claimedLootRewards);
        object.addProperty("lqrt", state.lastQuestRefreshTime);
        object.addProperty("lwrt", state.lastWeeklyRefreshTime);
        object.addProperty("v", updatedAt);
        JsonArray compact = new JsonArray();
        for (int index = 0; index < state.activeQuests.size(); index++) {
            PassQuest quest = state.activeQuests.get(index);
            compact.add(index);
            compact.add(quest.progress);
            compact.add(Math.max(1, quest.target));
            compact.add(quest.rewarded ? 1 : 0);
        }
        object.add("ct", compact);
        return GSON.toJson(object);
    }

    private static String toTaskJson(ProgressionState state) {
        JsonObject object = new JsonObject();
        JsonArray mapping = new JsonArray();
        JsonArray definitions = new JsonArray();
        for (int index = 0; index < state.activeQuests.size(); index++) {
            PassQuest quest = state.activeQuests.get(index);
            mapping.add(index);
            mapping.add(quest.progress);
            mapping.add(Math.max(1, quest.target));
            JsonObject definition = new JsonObject();
            definition.addProperty("i", quest.id == null ? "quest_" + index : quest.id);
            definition.addProperty("t", quest.title == null ? "" : quest.title);
            definition.addProperty("ds", quest.description == null ? "" : quest.description);
            definition.addProperty("ot", ProgressionObjectives.normalize(quest.objectiveType));
            definition.addProperty("ok", quest.objectiveKey == null ? "" : quest.objectiveKey);
            definition.addProperty("tg", Math.max(1, quest.target));
            definition.addProperty("rx", quest.rewardExperience);
            definition.addProperty("rc", quest.rewardCoins);
            definition.addProperty("rl", quest.rewardLoot);
            definition.addProperty("rd", quest.rewardCard == null ? "NONE" : quest.rewardCard.name());
            definition.addProperty("cg", quest.category == null ? "DAILY" : quest.category.name());
            definitions.add(definition);
        }
        object.add("m", mapping);
        object.add("d", definitions);
        object.addProperty("r", state.lastQuestRefreshTime);
        return GSON.toJson(object);
    }

    private static FactionCardType currentFaction(ServerPlayer player) {
        try {
            SRERole role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
            return FactionCardType.fromRole(role);
        } catch (RuntimeException ignored) {
            return FactionCardType.NONE;
        }
    }

    private static String currentGameModeId(ServerPlayer player) {
        try {
            var mode = SREGameWorldComponent.KEY.get(player.level()).getGameMode();
            return mode == null || mode.identifier == null ? null : mode.identifier.toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ServerPlayer onlinePlayer(UUID uuid) {
        if (uuid == null || SRE.SERVER == null) {
            return null;
        }
        return SRE.SERVER.getPlayerList().getPlayer(uuid);
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static final class Entry {
        private final UUID playerUuid;
        private ProgressionState state = ProgressionState.createDefault();
        private volatile boolean online;
        private volatile boolean dirty;
        private volatile boolean loaded;
        private volatile boolean loadInFlight;
        private volatile boolean saveInFlight;
        private volatile long updatedAt;
        private volatile long lastFlushAt;
        private volatile long lastQuestCheckAt;

        private Entry(UUID playerUuid) {
            this.playerUuid = playerUuid;
        }
    }
}

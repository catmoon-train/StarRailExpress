package io.wifi.starrailexpress.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.network.PlayerDataPartSyncPayload;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressionDataManager {
    public static final String PART = "progression";
    private static final Gson GSON = new GsonBuilder().create();
    private static final long FLUSH_INTERVAL_MS = 5_000L;
    private static final long FLUSH_TIMEOUT_MS = 4_000L;
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private ProgressionDataManager() {
    }

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(ProgressionDataManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(ProgressionDataManager::flushAllBlocking);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ENTRIES.clear());
    }

    public static ProgressionState get(ServerPlayer player) {
        return getEntry(player.getUUID()).state;
    }

    public static void onRoleAssigned(ServerPlayer player, SRERole role) {
        ProgressionState.FactionCardType matchedCard = ProgressionState.FactionCardType.fromRole(role);
        if (matchedCard != ProgressionState.FactionCardType.NONE) {
            grantExperience(player, 5);
        }
    }

    public static void onRoleAssigned(Player player, SRERole role) {
        if (player instanceof ServerPlayer serverPlayer) {
            onRoleAssigned(serverPlayer, role);
        }
    }

    public static void onRoundQuestFinished(ServerPlayer player, String questName) {
        grantExperience(player, 20);
    }

    public static void onRoundQuestFinished(Player player, String questName) {
        if (player instanceof ServerPlayer serverPlayer) {
            onRoundQuestFinished(serverPlayer, questName);
        }
    }

    public static void onPlayerKill(ServerPlayer player) {
        grantExperience(player, 15);
    }

    public static void onPlayerKillDifferentTeam(ServerPlayer player) {
        grantExperience(player, 50);
    }

    public static void onRoundSettled(ServerPlayer player, SRERole role, boolean isWinner) {
        grantExperience(player, isWinner ? 85 : 25);
        if (isWinner) {
            PlayerEconomyManager.addCoinNum(player, 20);
            getEntry(player.getUUID()).state.claimedCoinRewards += 20;
        }
        onRoleAssigned(player, role);
    }

    public static void onItemUsed(ServerPlayer player, String itemId) {
        grantExperience(player, 1);
    }

    public static void onPickupItem(ServerPlayer player, String itemId) {
        grantExperience(player, 1);
    }

    public static void addFactionCard(ServerPlayer player, ProgressionState.FactionCardType type, int count) {
        if (type == ProgressionState.FactionCardType.NONE || count == 0) {
            return;
        }
        Entry entry = getEntry(player.getUUID());
        int current = entry.state.factionCards.getOrDefault(type, 0);
        entry.state.factionCards.put(type, Math.max(0, current + count));
        markDirty(player, entry);
    }

    public static void addFactionCard(Player player, ProgressionState.FactionCardType type, int count) {
        if (player instanceof ServerPlayer serverPlayer) {
            addFactionCard(serverPlayer, type, count);
        }
    }

    public static boolean activateFactionCard(ServerPlayer player, ProgressionState.FactionCardType type) {
        Entry entry = getEntry(player.getUUID());
        int current = entry.state.factionCards.getOrDefault(type, 0);
        if (type == ProgressionState.FactionCardType.NONE || current < 1
                || PlayerRoleWeightManager.ForcePlayerTeam.containsKey(player.getUUID())) {
            return false;
        }
        PlayerRoleWeightManager.ForcePlayerTeam.put(player.getUUID(), type.getTypeId());
        entry.state.factionCards.put(type, current - 1);
        markDirty(player, entry);
        Component message = Component.translatable("message.sre.progression.faction_card_activated",
                Component.translatable(type.displayName));
        player.sendSystemMessage(message);
        player.displayClientMessage(message, true);
        return true;
    }

    public static boolean flushBlocking(UUID playerUuid) {
        Entry entry = ENTRIES.get(playerUuid);
        if (entry == null || !isDatabaseEnabled()) {
            return false;
        }
        boolean success = MysqlPlayerDataStore.saveBatchBlocking(
                playerUuid,
                Map.of(PART, toJson(entry.state, entry.updatedAt)),
                Math.max(1L, entry.updatedAt),
                FLUSH_TIMEOUT_MS);
        if (success) {
            entry.dirty = false;
        }
        return success;
    }

    private static void grantExperience(ServerPlayer player, int amount) {
        if (!SREConfig.instance().enableProgressionSystem || amount <= 0) {
            return;
        }
        Entry entry = getEntry(player.getUUID());
        entry.state.experience += amount;
        entry.state.totalExperience += amount;
        while (entry.state.experience >= entry.state.getExperienceForNextLevel()) {
            entry.state.experience -= entry.state.getExperienceForNextLevel();
            entry.state.level++;
            int coinReward = 20 + entry.state.level * 2;
            PlayerEconomyManager.addCoinNum(player, coinReward);
            entry.state.claimedCoinRewards += coinReward;
            if (entry.state.level % 5 == 0) {
                PlayerEconomyManager.addLootChance(player, 1);
                entry.state.claimedLootRewards++;
            }
        }
        markDirty(player, entry);
    }

    private static void onJoin(ServerPlayer player) {
        Entry entry = getEntry(player.getUUID());
        entry.online = true;
        send(player, entry);
        if (!isDatabaseEnabled()) {
            return;
        }
        entry.loadInFlight = true;
        MysqlPlayerDataStore.loadBatchAsync(player.getUUID(), List.of(PART))
                .whenComplete((records, throwable) -> {
                    entry.loadInFlight = false;
                    MinecraftServer server = player.getServer();
                    if (server == null) {
                        return;
                    }
                    server.execute(() -> {
                        if (throwable != null) {
                            SRE.LOGGER.warn("Failed to load progression part for {}", player.getUUID(), throwable);
                            return;
                        }
                        MysqlPlayerDataStore.SyncRecord record = records.get(PART);
                        if (record != null && record.payload() != null && !record.payload().isBlank()) {
                            entry.state = fromJson(record.payload());
                            entry.updatedAt = Math.max(entry.updatedAt, record.updatedAt());
                            entry.dirty = false;
                        }
                        send(player, entry);
                    });
                });
    }

    private static void onDisconnect(ServerPlayer player) {
        Entry entry = ENTRIES.get(player.getUUID());
        if (entry != null) {
            flushBlocking(player.getUUID());
            ENTRIES.remove(player.getUUID(), entry);
        }
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Entry entry = ENTRIES.get(player.getUUID());
            if (entry == null || !entry.online || !entry.dirty || entry.saveInFlight
                    || now - entry.lastFlushAt < FLUSH_INTERVAL_MS) {
                continue;
            }
            flushAsync(player.getUUID(), entry);
        }
    }

    private static void flushAsync(UUID playerUuid, Entry entry) {
        if (!isDatabaseEnabled() || entry.loadInFlight) {
            return;
        }
        entry.saveInFlight = true;
        entry.dirty = false;
        entry.lastFlushAt = System.currentTimeMillis();
        long updatedAt = Math.max(1L, entry.updatedAt);
        MysqlPlayerDataStore.saveBatchAsync(playerUuid, Map.of(PART, toJson(entry.state, updatedAt)), updatedAt)
                .whenComplete((success, throwable) -> {
                    entry.saveInFlight = false;
                    if (throwable != null || !Boolean.TRUE.equals(success)) {
                        entry.dirty = true;
                        if (throwable != null) {
                            SRE.LOGGER.warn("Failed to save progression part for {}", playerUuid, throwable);
                        }
                    }
                });
    }

    private static void flushAllBlocking(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            flushBlocking(player.getUUID());
        }
    }

    private static Entry getEntry(UUID uuid) {
        return ENTRIES.computeIfAbsent(uuid, ignored -> new Entry());
    }

    private static void markDirty(ServerPlayer player, Entry entry) {
        entry.updatedAt = Math.max(System.currentTimeMillis(), entry.updatedAt + 1L);
        entry.state.version = entry.updatedAt;
        entry.dirty = true;
        send(player, entry);
    }

    private static void send(ServerPlayer player, Entry entry) {
        ServerPlayNetworking.send(player,
                new PlayerDataPartSyncPayload(player.getUUID(), PART, toJson(entry.state, entry.updatedAt), entry.updatedAt));
    }

    private static boolean isDatabaseEnabled() {
        return SREConfig.instance().mysqlPlayerSyncEnabled && MysqlPlayerDataStore.isAvailable();
    }

    private static ProgressionState fromJson(String json) {
        try {
            ProgressionState state = GSON.fromJson(json, ProgressionState.class);
            return state == null ? ProgressionState.createDefault() : state.normalized();
        } catch (RuntimeException exception) {
            return ProgressionState.createDefault();
        }
    }

    private static String toJson(ProgressionState state, long updatedAt) {
        state.version = updatedAt;
        return GSON.toJson(state.normalized());
    }

    private static final class Entry {
        private ProgressionState state = ProgressionState.createDefault();
        private volatile boolean online;
        private volatile boolean dirty;
        private volatile boolean loadInFlight;
        private volatile boolean saveInFlight;
        private volatile long updatedAt;
        private volatile long lastFlushAt;
    }
}

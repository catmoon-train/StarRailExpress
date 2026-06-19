package io.wifi.starrailexpress.stats;

import com.google.gson.JsonSyntaxException;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.game.data.PlayerStatsData;
import io.wifi.starrailexpress.network.PlayerDataPartSyncPayload;
import io.wifi.starrailexpress.network.PlayerStatsSyncPayload;
import io.wifi.starrailexpress.util.PlayerStatsSerializer;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStatsManager {
    private static final String DATABASE_KEY = "stats";
    private static final String STATS_DIR = "play_stats";
    private static final long SAVE_INTERVAL_MS = 5_000L;
    private static final long DATABASE_LOAD_RETRY_MS = 15_000L;
    private static final long DATABASE_FLUSH_TIMEOUT_MS = 4_000L;
    private static final Map<UUID, Entry> STATS = new ConcurrentHashMap<>();

    private PlayerStatsManager() {
    }

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onPlayerDisconnect(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(PlayerStatsManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(PlayerStatsManager::flushAllLocalBlocking);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATS.clear());
    }

    public static PlayerStats get(ServerPlayer player) {
        return get(player.getUUID());
    }

    public static PlayerStats get(Player player) {
        return get(player.getUUID());
    }

    public static PlayerStats get(UUID playerUuid) {
        return STATS.computeIfAbsent(playerUuid, PlayerStatsManager::newEntry).stats;
    }

    public static void syncTo(ServerPlayer viewer, UUID targetUuid) {
        Entry entry = STATS.get(targetUuid);
        if (entry == null) {
            return;
        }
        sendSnapshot(viewer, entry);
    }

    public static void flushDatabaseAsync(UUID playerUuid) {
        Entry entry = STATS.get(playerUuid);
        if (isOnlineEntry(entry)) {
            saveDatabaseAsync(null, entry, true);
        }
    }

    public static boolean flushDatabaseBlocking(UUID playerUuid) {
        Entry entry = STATS.get(playerUuid);
        if (!isOnlineEntry(entry) || !isDatabaseEnabled() || !entry.databaseLoaded) {
            return false;
        }
        long updatedAt = Math.max(1L, entry.updatedAt);
        boolean success = MysqlPlayerDataStore.saveBatchBlocking(
                playerUuid,
                Map.of(DATABASE_KEY, PlayerStatsSerializer.toJson(entry.stats, updatedAt)),
                updatedAt,
                DATABASE_FLUSH_TIMEOUT_MS);
        if (success) {
            entry.databaseDirty = false;
            entry.lastDatabaseSaveTime = System.currentTimeMillis();
        }
        return success;
    }

    private static Entry newEntry(UUID playerUuid) {
        Entry entry = new Entry(new PlayerStats(playerUuid));
        entry.stats.setDirtyListener(() -> markDirty(entry));
        return entry;
    }

    private static void markDirty(Entry entry) {
        long now = System.currentTimeMillis();
        entry.updatedAt = Math.max(now, entry.updatedAt + 1L);
        entry.localDirty = true;
        entry.databaseDirty = true;
        entry.clientDirty = true;
    }

    private static void onPlayerJoin(ServerPlayer player) {
        Entry entry = STATS.computeIfAbsent(player.getUUID(), PlayerStatsManager::newEntry);
        entry.online = true;
        loadLocal(entry);
        sendSnapshot(player, entry);
        pullDatabase(player, entry);
    }

    private static void onPlayerDisconnect(ServerPlayer player) {
        Entry entry = STATS.get(player.getUUID());
        if (entry == null) {
            return;
        }
        entry.online = false;
        entry.forceDatabaseFlushPending = false;
        saveLocalBlocking(entry);
        STATS.remove(player.getUUID(), entry);
    }

    private static void tick(MinecraftServer server) {
        if (!SREConfig.instance().isStatsEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Entry entry = STATS.get(player.getUUID());
            if (!isOnlineEntry(entry)) {
                continue;
            }
            if (entry.localDirty && !entry.localSaveInFlight
                    && now - entry.lastLocalSaveTime >= SAVE_INTERVAL_MS) {
                saveLocalAsync(entry);
            }
            if (entry.databaseDirty && !entry.databaseSaveInFlight
                    && now - entry.lastDatabaseSaveTime >= SAVE_INTERVAL_MS) {
                saveDatabaseAsync(player, entry, false);
            }
            if (isDatabaseEnabled() && !entry.databaseLoaded && !entry.databaseLoadInFlight
                    && now - entry.lastDatabaseLoadAttempt >= DATABASE_LOAD_RETRY_MS) {
                pullDatabase(player, entry);
            }
            if (entry.clientDirty && now - entry.lastClientSyncTime >= SAVE_INTERVAL_MS) {
                sendSnapshot(player, entry);
            }
        }
    }

    private static void loadLocal(Entry entry) {
        Path path = getSaveFilePath(entry.stats.getPlayerUuid());
        entry.localLoaded = true;
        if (!Files.exists(path)) {
            entry.updatedAt = 0L;
            return;
        }
        try {
            PlayerStatsData data = PlayerStatsSerializer.fromJson(Files.readString(path, StandardCharsets.UTF_8));
            entry.stats.replaceWith(data);
            long fileTime = Files.getLastModifiedTime(path).toMillis();
            entry.updatedAt = data != null && data.getUpdatedAt() > 0L ? data.getUpdatedAt() : fileTime;
            entry.lastPersistedUpdatedAt = entry.updatedAt;
            entry.localDirty = false;
            entry.databaseDirty = false;
            entry.clientDirty = false;
            SRE.LOGGER.info("Loaded player stats for {} from local storage", entry.stats.getPlayerUuid());
        } catch (IOException | JsonSyntaxException exception) {
            SRE.LOGGER.error("Failed to load local stats for {}", entry.stats.getPlayerUuid(), exception);
        }
    }

    private static void pullDatabase(ServerPlayer player, Entry entry) {
        if (!isOnlineEntry(entry) || !isDatabaseEnabled() || entry.databaseLoadInFlight) {
            return;
        }
        entry.databaseLoadInFlight = true;
        entry.lastDatabaseLoadAttempt = System.currentTimeMillis();
        MysqlPlayerDataStore.loadBatchAsync(player.getUUID(), List.of(DATABASE_KEY))
                .whenComplete((records, throwable) -> {
                    entry.databaseLoadInFlight = false;
                    if (throwable != null) {
                        SRE.LOGGER.warn("Failed to load player stats from MySQL for {}", player.getUUID(), throwable);
                        return;
                    }
                    MinecraftServer server = player.getServer();
                    if (server == null) {
                        return;
                    }
                    server.execute(() -> applyDatabaseRecords(player, entry, records));
                });
    }

    private static void applyDatabaseRecords(ServerPlayer player, Entry entry,
            Map<String, MysqlPlayerDataStore.SyncRecord> records) {
        if (STATS.get(player.getUUID()) != entry) {
            return;
        }
        MysqlPlayerDataStore.SyncRecord record = records.get(DATABASE_KEY);
        if (record == null || record.payload() == null || record.payload().isBlank()) {
            entry.databaseDirty = entry.updatedAt > 0L;
            entry.databaseLoaded = true;
            return;
        }
        try {
            PlayerStatsData data = PlayerStatsSerializer.fromJson(record.payload());
            long databaseUpdatedAt = Math.max(record.updatedAt(), data == null ? 0L : data.getUpdatedAt());
            entry.databaseLoaded = true;
            boolean forceApplyRemote = entry.databaseConflictPending;
            entry.databaseConflictPending = false;
            if (forceApplyRemote || databaseUpdatedAt > entry.updatedAt) {
                entry.stats.replaceWith(data);
                entry.updatedAt = Math.max(entry.updatedAt, databaseUpdatedAt);
                entry.localDirty = true;
                entry.databaseDirty = false;
                entry.clientDirty = true;
                saveLocalAsync(entry);
                sendSnapshot(player, entry);
            } else if (entry.updatedAt > databaseUpdatedAt) {
                entry.databaseDirty = true;
            }
        } catch (JsonSyntaxException exception) {
            entry.databaseLoaded = false;
            SRE.LOGGER.error("Failed to parse MySQL stats for {}", player.getUUID(), exception);
        }
    }

    private static void sendSnapshot(ServerPlayer player, Entry entry) {
        if (!SREConfig.instance().isStatsEnabled) {
            return;
        }
        String json = PlayerStatsSerializer.toJson(entry.stats, entry.updatedAt);
        ServerPlayNetworking.send(player, new PlayerStatsSyncPayload(entry.stats.getPlayerUuid(), json));
        ServerPlayNetworking.send(player, new PlayerDataPartSyncPayload(entry.stats.getPlayerUuid(), "stats", json, entry.updatedAt));
        entry.clientDirty = false;
        entry.lastClientSyncTime = System.currentTimeMillis();
    }

    private static void saveLocalAsync(Entry entry) {
        if (entry.localSaveInFlight) {
            return;
        }
        entry.localSaveInFlight = true;
        entry.localDirty = false;
        entry.lastLocalSaveTime = System.currentTimeMillis();
        long updatedAt = entry.updatedAt;
        String json = PlayerStatsSerializer.toJson(entry.stats, updatedAt);
        Util.ioPool().execute(() -> {
            try {
                writeLocal(entry, json, updatedAt);
            } catch (IOException exception) {
                entry.localDirty = true;
                SRE.LOGGER.error("Failed to save local stats for {}", entry.stats.getPlayerUuid(), exception);
            } finally {
                entry.localSaveInFlight = false;
            }
        });
    }

    private static void saveLocalBlocking(Entry entry) {
        if (!entry.localLoaded && !entry.localDirty) {
            return;
        }
        try {
            long updatedAt = entry.updatedAt;
            writeLocal(entry, PlayerStatsSerializer.toJson(entry.stats, updatedAt), updatedAt);
            entry.localDirty = false;
            entry.lastLocalSaveTime = System.currentTimeMillis();
        } catch (IOException exception) {
            SRE.LOGGER.error("Failed to flush local stats for {}", entry.stats.getPlayerUuid(), exception);
        }
    }

    private static void writeLocal(Entry entry, String json, long updatedAt) throws IOException {
        synchronized (entry.localWriteLock) {
            if (updatedAt < entry.lastPersistedUpdatedAt) {
                return;
            }
            Path path = getSaveFilePath(entry.stats.getPlayerUuid());
            Files.createDirectories(path.getParent());
            Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temporary, json, StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
                entry.lastPersistedUpdatedAt = updatedAt;
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void saveDatabaseAsync(ServerPlayer player, Entry entry, boolean force) {
        if (!isOnlineEntry(entry) || !isDatabaseEnabled() || !entry.databaseLoaded
                || (!force && !entry.databaseDirty)) {
            return;
        }
        if (entry.databaseSaveInFlight) {
            entry.forceDatabaseFlushPending |= force;
            return;
        }
        entry.databaseSaveInFlight = true;
        entry.databaseDirty = false;
        entry.lastDatabaseSaveTime = System.currentTimeMillis();
        long updatedAt = Math.max(1L, entry.updatedAt);
        String json = PlayerStatsSerializer.toJson(entry.stats, updatedAt);
        MysqlPlayerDataStore.saveBatchAsync(
                entry.stats.getPlayerUuid(),
                Map.of(DATABASE_KEY, json),
                updatedAt)
                .whenComplete((success, throwable) -> {
                    entry.databaseSaveInFlight = false;
                    if (throwable != null || !Boolean.TRUE.equals(success)) {
                    entry.databaseDirty = true;
                    if (throwable != null) {
                        SRE.LOGGER.warn("Failed to save player stats to MySQL for {}",
                                entry.stats.getPlayerUuid(), throwable);
                    } else if (player != null) {
                        entry.databaseConflictPending = true;
                        pullDatabase(player, entry);
                    }
                }
                if (entry.forceDatabaseFlushPending) {
                    entry.forceDatabaseFlushPending = false;
                    saveDatabaseAsync(player, entry, true);
                }
            });
    }

    private static void flushAllLocalBlocking(MinecraftServer server) {
        for (Entry entry : STATS.values()) {
            saveLocalBlocking(entry);
        }
    }

    private static boolean isDatabaseEnabled() {
        return SREConfig.instance().isStatsEnabled
                && SREConfig.instance().isStatsSyncEnabled
                && SREConfig.instance().mysqlPlayerSyncEnabled
                && MysqlPlayerDataStore.isAvailable();
    }

    private static boolean isOnlineEntry(Entry entry) {
        return entry != null
                && entry.online
                && STATS.get(entry.stats.getPlayerUuid()) == entry;
    }

    private static Path getSaveFilePath(UUID playerUuid) {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(STATS_DIR)
                .resolve(playerUuid + ".json");
    }

    private static final class Entry {
        private final PlayerStats stats;
        private final Object localWriteLock = new Object();
        private volatile boolean online;
        private volatile boolean localLoaded;
        private volatile boolean databaseLoaded;
        private volatile boolean databaseLoadInFlight;
        private volatile boolean localDirty;
        private volatile boolean databaseDirty;
        private volatile boolean clientDirty;
        private volatile boolean localSaveInFlight;
        private volatile boolean databaseSaveInFlight;
        private volatile boolean forceDatabaseFlushPending;
        private volatile boolean databaseConflictPending;
        private volatile long updatedAt;
        private volatile long lastPersistedUpdatedAt;
        private volatile long lastLocalSaveTime;
        private volatile long lastDatabaseSaveTime;
        private volatile long lastDatabaseLoadAttempt;
        private volatile long lastClientSyncTime;

        private Entry(PlayerStats stats) {
            this.stats = stats;
        }
    }
}

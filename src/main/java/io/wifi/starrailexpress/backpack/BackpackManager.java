package io.wifi.starrailexpress.backpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.network.PlayerDataPartSyncPayload;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 场外背包数据管理器（MySQL 分区 {@code data_key="backpack"}）。
 * 结构镜像 {@link ProgressionDataManager}：内存缓存 + 入服异步加载 + 脏标记周期 flush + 断线/关服阻塞 flush。
 * 是阵营卡牌迁移后的唯一来源；{@code ProgressionDataManager.addFactionCard/activateFactionCard} 委托至此。
 */
public final class BackpackManager {
    public static final String PART = "backpack";
    private static final Gson GSON = new GsonBuilder().create();
    private static final long FLUSH_INTERVAL_MS = 5_000L;
    private static final long FLUSH_TIMEOUT_MS = 4_000L;
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private BackpackManager() {
    }

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(BackpackManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(BackpackManager::flushAllBlocking);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ENTRIES.clear());
    }

    // ====================== 公开服务端 API ======================

    public static Map<FactionCardType, Integer> getCards(ServerPlayer player) {
        return new HashMap<>(getEntry(player.getUUID()).state.cards);
    }

    public static int getCardCount(ServerPlayer player, FactionCardType type) {
        return getEntry(player.getUUID()).state.cards.getOrDefault(type, 0);
    }

    public static void addCard(ServerPlayer player, FactionCardType type, int count) {
        if (type == FactionCardType.NONE || count == 0) {
            return;
        }
        Entry entry = getEntry(player.getUUID());
        int current = entry.state.cards.getOrDefault(type, 0);
        entry.state.cards.put(type, Math.max(0, current + count));
        markDirty(player, entry);
    }

    /** 逐字复刻 {@code ProgressionDataManager.activateFactionCard}：卡库写改为背包。 */
    public static boolean activateCard(ServerPlayer player, FactionCardType type) {
        Entry entry = getEntry(player.getUUID());
        int current = entry.state.cards.getOrDefault(type, 0);
        if (type == FactionCardType.NONE || current < 1
                || PlayerRoleWeightManager.ForcePlayerTeam.containsKey(player.getUUID())) {
            return false;
        }
        PlayerRoleWeightManager.ForcePlayerTeam.put(player.getUUID(), type.getTypeId());
        entry.state.cards.put(type, current - 1);
        markDirty(player, entry);
        Component message = Component.translatable("message.sre.progression.faction_card_activated",
                Component.translatable(type.displayName));
        player.sendSystemMessage(message);
        player.displayClientMessage(message, true);
        return true;
    }

    /** 命令开屏前可调用以保证客户端数据新鲜。 */
    public static void resend(ServerPlayer player) {
        send(player, getEntry(player.getUUID()));
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
                Map.of(PART, toJson(entry.state, entry.updatedAt)),
                Math.max(1L, entry.updatedAt),
                FLUSH_TIMEOUT_MS);
        if (success) {
            entry.dirty = false;
        }
        return success;
    }

    // ====================== 迁移（移动，一次性） ======================

    /**
     * 把通行证的 {@code factionCards} 计数搬入背包并清零通行证侧。须在背包与通行证两侧 DB 记录都加载完成后调用，
     * 在 {@link ProgressionDataManager#reloadFromDatabase} 与本类 {@link #reloadFromDatabase} 完成时各触发一次。
     * 严格顺序：先落背包并置 migrated，再清/落通行证 —— 任一步失败都不会丢卡或重复计数。
     */
    public static void migrateIfNeeded(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Entry entry = ENTRIES.get(uuid);
        if (entry == null || !entry.loaded || !ProgressionDataManager.isLoaded(uuid)) {
            return; // 等两侧都加载完，由后完成者再触发
        }
        BackpackState bp = entry.state;
        ProgressionState pg = ProgressionDataManager.get(player);

        if (bp.migrated) {
            // 已迁移：清理可能残留的陈旧通行证卡牌（崩溃于清源持久化前的情形）
            boolean stale = false;
            for (FactionCardType type : FactionCardType.values()) {
                if (type != FactionCardType.NONE && pg.factionCards.getOrDefault(type, 0) > 0) {
                    pg.factionCards.put(type, 0);
                    stale = true;
                }
            }
            if (stale) {
                ProgressionDataManager.markFactionCardsCleared(player);
                ProgressionDataManager.flushBlocking(uuid);
            }
            return;
        }

        // 1) 加法合并通行证卡牌进背包
        for (FactionCardType type : FactionCardType.values()) {
            if (type == FactionCardType.NONE) {
                continue;
            }
            int c = pg.factionCards.getOrDefault(type, 0);
            if (c > 0) {
                bp.cards.merge(type, c, Integer::sum);
            }
        }
        // 2) 置 migrated 并先落背包（卡牌绝不会丢）
        bp.migrated = true;
        markDirty(player, entry);
        flushBlocking(uuid);
        // 3) 清空通行证侧并持久化
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                pg.factionCards.put(type, 0);
            }
        }
        ProgressionDataManager.markFactionCardsCleared(player);
        ProgressionDataManager.flushBlocking(uuid);
        SRE.LOGGER.info("Migrated faction cards from progression to backpack for {}", uuid);
    }

    // ====================== 生命周期 ======================

    private static void onJoin(ServerPlayer player) {
        Entry entry = getEntry(player.getUUID());
        entry.online = true;
        send(player, entry);
        if (!isDatabaseEnabled()) {
            entry.loaded = true;
            migrateIfNeeded(player);
            return;
        }
        reloadFromDatabase(player, entry);
    }

    private static void reloadFromDatabase(ServerPlayer player, Entry entry) {
        if (!isDatabaseEnabled() || entry.loadInFlight) {
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
                        if (ENTRIES.get(player.getUUID()) != entry) {
                            return;
                        }
                        if (throwable != null) {
                            SRE.LOGGER.warn("Failed to load backpack part for {}", player.getUUID(), throwable);
                            return;
                        }
                        MysqlPlayerDataStore.SyncRecord record = records.get(PART);
                        if (record != null && record.payload() != null && !record.payload().isBlank()) {
                            entry.state = fromJson(record.payload());
                            entry.updatedAt = Math.max(entry.updatedAt, record.updatedAt());
                            entry.dirty = false;
                        }
                        entry.loaded = true;
                        send(player, entry);
                        migrateIfNeeded(player);
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
        MysqlPlayerDataStore.saveBatchAsync(player.getUUID(), Map.of(PART, toJson(entry.state, updatedAt)), updatedAt)
                .whenComplete((success, throwable) -> {
                    entry.saveInFlight = false;
                    if (throwable != null || !Boolean.TRUE.equals(success)) {
                        entry.dirty = true;
                        if (throwable != null) {
                            SRE.LOGGER.warn("Failed to save backpack part for {}", player.getUUID(), throwable);
                        } else {
                            reloadFromDatabase(player, entry);
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
                new PlayerDataPartSyncPayload(player.getUUID(), PART, toJson(entry.state, entry.updatedAt),
                        entry.updatedAt));
    }

    private static boolean isDatabaseEnabled() {
        return SREConfig.instance().mysqlPlayerSyncEnabled && MysqlPlayerDataStore.isAvailable();
    }

    private static BackpackState fromJson(String json) {
        try {
            BackpackState state = GSON.fromJson(json, BackpackState.class);
            return state == null ? BackpackState.createDefault() : state.normalized();
        } catch (RuntimeException exception) {
            return BackpackState.createDefault();
        }
    }

    private static String toJson(BackpackState state, long updatedAt) {
        state.version = updatedAt;
        return GSON.toJson(state.normalized());
    }

    private static final class Entry {
        private BackpackState state = BackpackState.createDefault();
        private volatile boolean online;
        private volatile boolean dirty;
        private volatile boolean loaded;
        private volatile boolean loadInFlight;
        private volatile boolean saveInFlight;
        private volatile long updatedAt;
        private volatile long lastFlushAt;
    }
}

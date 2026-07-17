package io.wifi.starrailexpress.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.network.PlayerDataPartSyncPayload;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerEconomyManager {
    public static final String PART = "economy";
    private static final Gson GSON = new GsonBuilder().create();
    private static final long FLUSH_INTERVAL_MS = 5_000L;
    private static final long FLUSH_TIMEOUT_MS = 4_000L;
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private PlayerEconomyManager() {
    }

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(PlayerEconomyManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(PlayerEconomyManager::flushAllBlocking);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ENTRIES.clear());
    }

    public static int getLootChance(Player player) {
        return get(player.getUUID()).state.lootChance;
    }

    public static int getCoinNum(Player player) {
        return get(player.getUUID()).state.coinNum;
    }

    public static void addLootChance(Player player, int delta) {
        Entry entry = get(player.getUUID());
        entry.state.lootChance = Math.max(0, entry.state.lootChance + delta);
        markDirty(player, entry);
    }

    public static void addCoinNum(Player player, int delta) {
        Entry entry = get(player.getUUID());
        entry.state.coinNum = Math.max(0, entry.state.coinNum + delta);
        markDirty(player, entry);
    }

    public static String getEquippedSkin(Player player, ItemStack stack) {
        String itemType = itemType(stack);
        return getEquippedSkinForItemType(player, itemType);
    }

    public static String getEquippedSkinForItemType(Player player, String itemType) {
        if (player.level().isClientSide()) {
            return io.wifi.starrailexpress.client.data.ClientPlayerDataCache
                    .economy(player.getUUID())
                    .getEquippedSkin(normalizeItemName(itemType));
        }
        return get(player.getUUID()).state.equipped.getOrDefault(normalizeItemName(itemType), "default");
    }

    public static void setEquippedSkinForItemType(Player player, String itemType, String skinName) {
        String normalizedType = normalizeItemName(itemType);
        Entry entry = get(player.getUUID());
        entry.state.equipped.put(normalizedType, skinName == null || skinName.isBlank() ? "default" : skinName);
        markDirty(player, entry);
    }

    public static boolean isSkinUnlocked(Player player, ItemStack stack, String skinName) {
        return isSkinUnlockedForItemType(player, itemType(stack), skinName);
    }

    public static boolean isSkinUnlockedForItemType(Player player, String itemType, String skinName) {
        String normalizedType = normalizeItemName(itemType);
        if ("default".equals(skinName)) {
            return true;
        }
        if (player.level().isClientSide()) {
            return io.wifi.starrailexpress.client.data.ClientPlayerDataCache
                    .economy(player.getUUID())
                    .isSkinUnlocked(normalizedType, skinName);
        }
        Map<String, Boolean> skins = get(player.getUUID()).state.unlocked.get(normalizedType);
        return skins != null && Boolean.TRUE.equals(skins.get(skinName));
    }

    public static void unlockSkin(Player player, ItemStack stack, String skinName) {
        unlockSkinForItemType(player, itemType(stack), skinName);
    }

    public static void unlockSkinForItemType(Player player, String itemType, String skinName) {
        if (skinName == null || skinName.isBlank()) {
            return;
        }
        Entry entry = get(player.getUUID());
        entry.state.unlocked
                .computeIfAbsent(normalizeItemName(itemType), ignored -> new ConcurrentHashMap<>())
                .put(skinName, true);
        markDirty(player, entry);
    }

    public static void lockSkinForItemType(Player player, String itemType, String skinName) {
        Entry entry = get(player.getUUID());
        Map<String, Boolean> skins = entry.state.unlocked.get(normalizeItemName(itemType));
        if (skins != null) {
            skins.remove(skinName);
            if (skins.isEmpty()) {
                entry.state.unlocked.remove(normalizeItemName(itemType));
            }
            markDirty(player, entry);
        }
    }

    public static Map<String, String> getEquippedSkins(Player player) {
        return new HashMap<>(get(player.getUUID()).state.equipped);
    }

    public static Map<String, Map<String, Boolean>> getUnlockedSkins(Player player) {
        Map<String, Map<String, Boolean>> copy = new HashMap<>();
        get(player.getUUID()).state.unlocked.forEach((type, skins) -> copy.put(type, new HashMap<>(skins)));
        return copy;
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

    private static void onJoin(ServerPlayer player) {
        Entry entry = get(player.getUUID());
        entry.online = true;
        send(player, entry);
        if (!isDatabaseEnabled()) {
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
                            SRE.LOGGER.warn("Failed to load economy part for {}", player.getUUID(), throwable);
                            return;
                        }
                        MysqlPlayerDataStore.SyncRecord record = records.get(PART);
                        if (record != null && record.payload() != null && !record.payload().isBlank()) {
                            EconomyState loaded = fromJson(record.payload());
                            entry.state.copyFrom(loaded);
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
            // Use async flush to avoid blocking the network thread.
            // Final data is guaranteed by SERVER_STOPPING → flushAllBlocking.
            flushAsync(player, entry);
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
                            SRE.LOGGER.warn("Failed to save economy part for {}", player.getUUID(), throwable);
                        } else {
                            reloadFromDatabase(player, entry);
                        }
                    }
                });
    }

    private static void flushAllBlocking(MinecraftServer server) {
        if (server.getPlayerList() == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            flushBlocking(player.getUUID());
        }
    }

    private static Entry get(UUID uuid) {
        return ENTRIES.computeIfAbsent(uuid, ignored -> new Entry());
    }

    private static void markDirty(Player player, Entry entry) {
        entry.updatedAt = Math.max(System.currentTimeMillis(), entry.updatedAt + 1L);
        entry.state.version = entry.updatedAt;
        entry.dirty = true;
        if (player instanceof ServerPlayer serverPlayer) {
            send(serverPlayer, entry);
        }
    }

    private static void send(ServerPlayer player, Entry entry) {
        ServerPlayNetworking.send(player,
                new PlayerDataPartSyncPayload(player.getUUID(), PART, toJson(entry.state, entry.updatedAt), entry.updatedAt));
    }

    private static boolean isDatabaseEnabled() {
        return SREConfig.instance().mysqlPlayerSyncEnabled && MysqlPlayerDataStore.isAvailable();
    }

    private static String itemType(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(java.util.Locale.ROOT);
    }

    public static String normalizeItemName(String itemTypeName) {
        if (itemTypeName == null || itemTypeName.isBlank()) {
            return "default";
        }
        var id = net.minecraft.resources.ResourceLocation.tryParse(itemTypeName);
        return id == null ? itemTypeName : id.getPath();
    }

    private static EconomyState fromJson(String json) {
        try {
            EconomyState state = GSON.fromJson(json, EconomyState.class);
            return state == null ? new EconomyState() : state.normalized();
        } catch (RuntimeException exception) {
            return new EconomyState();
        }
    }

    private static String toJson(EconomyState state, long updatedAt) {
        state.version = updatedAt;
        return GSON.toJson(state);
    }

    private static final class Entry {
        private final EconomyState state = new EconomyState();
        private volatile boolean online;
        private volatile boolean dirty;
        private volatile boolean loadInFlight;
        private volatile boolean saveInFlight;
        private volatile long updatedAt;
        private volatile long lastFlushAt;
    }

    public static final class EconomyState {
        public Map<String, String> equipped = new ConcurrentHashMap<>();
        public Map<String, Map<String, Boolean>> unlocked = new ConcurrentHashMap<>();
        public int lootChance;
        public int coinNum;
        public long version;

        private void copyFrom(EconomyState other) {
            this.equipped = new ConcurrentHashMap<>(other.equipped);
            this.unlocked = new ConcurrentHashMap<>();
            other.unlocked.forEach((type, skins) -> this.unlocked.put(type, new ConcurrentHashMap<>(skins)));
            this.lootChance = Math.max(0, other.lootChance);
            this.coinNum = Math.max(0, other.coinNum);
            this.version = other.version;
        }

        private EconomyState normalized() {
            if (equipped == null) {
                equipped = new ConcurrentHashMap<>();
            }
            if (unlocked == null) {
                unlocked = new ConcurrentHashMap<>();
            }
            lootChance = Math.max(0, lootChance);
            coinNum = Math.max(0, coinNum);
            return this;
        }
    }
}

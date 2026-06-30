package io.wifi.starrailexpress.shop.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.scenery.SceneAssetCodec;
import io.wifi.starrailexpress.shop.ShopPriceTable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端商店价格缓存：把服务端同步来的价格表按哈希存到本地（最多 5 份，LRU 淘汰），并在打开商店时用于覆盖显示价。
 *
 * <p>Client-side shop-price cache. Persists server-synced price tables locally keyed by SHA-256 hash
 * (max {@link #MAX_FILES} files, LRU eviction) so that re-joining a server whose config is unchanged
 * needs only a hash handshake — the server skips re-sending the full content. Also exposes
 * {@link #overrideBasePrice} which the shop screen uses to display the synced (server-authoritative)
 * base price.
 *
 * <p>不同服务器配置不同，故本地保留多份；上限 {@link #MAX_FILES} 份。 / Different servers have different
 * configs, so several files are kept locally, capped at {@link #MAX_FILES}.
 */
@Environment(EnvType.CLIENT)
public final class ShopPriceClientCache {

    /** 本地最多保留的配置文件数量。 / Maximum number of cached config files. */
    public static final int MAX_FILES = 5;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("sre_shop_cache");
    private static final Path INDEX_FILE = ROOT.resolve("index.json");

    /** 当前已应用的价格表（来自缓存命中或服务端下发）。 / Currently applied price table. */
    private static volatile ShopPriceTable applied;
    /** 当前应用的哈希。 / Hash of the applied table. */
    private static volatile String appliedHash;

    private static CacheIndex index;
    private static boolean initialized;

    private ShopPriceClientCache() {
    }

    /** LRU 索引：哈希 -> 最近访问时间（毫秒）。 / LRU index: hash -> last access millis. */
    private static final class CacheIndex {
        private Map<String, Long> entries = new HashMap<>();
    }

    private static synchronized void ensureInit() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Files.createDirectories(ROOT);
        } catch (IOException e) {
            SRE.LOGGER.warn("[ShopPriceCache] failed to create cache dir", e);
        }
        index = loadIndex();
    }

    // ------------------------------------------------------------------
    // 握手 / 数据接收 / Handshake & data reception
    // ------------------------------------------------------------------

    /**
     * 处理服务端握手：若本地已缓存相同哈希则直接应用并返回 {@code true}（无需服务端再发送）；否则返回 {@code false}。
     * Handle the server handshake: if a cached config with this hash exists, apply it and return
     * {@code true} (server need not send anything); otherwise return {@code false}.
     */
    public static synchronized boolean handleHandshake(String hash) {
        ensureInit();
        if (hash == null || !SceneAssetCodec.isValidHash(hash)) {
            return false;
        }
        if (hash.equals(appliedHash)) {
            return true; // already applied this exact table
        }
        Path file = cachePath(hash);
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            byte[] data = Files.readAllBytes(file);
            if (!SceneAssetCodec.sha256(data).equals(hash)) {
                // 文件损坏/不一致：删除并视为未命中。 / corrupt: drop and miss.
                Files.deleteIfExists(file);
                index.entries.remove(hash);
                saveIndex();
                return false;
            }
            apply(hash, ShopPriceTable.fromBytes(data));
            touch(hash);
            return true;
        } catch (Exception e) {
            SRE.LOGGER.warn("[ShopPriceCache] failed to load cached table {}", hash, e);
            return false;
        }
    }

    /**
     * 处理服务端下发的完整数据：校验哈希后应用，并写入本地缓存（超额按 LRU 淘汰）。
     * Apply server-sent full data after verifying the hash, and persist it (LRU-evicting on overflow).
     */
    public static synchronized void handleData(String hash, byte[] data) {
        ensureInit();
        if (data == null) {
            return;
        }
        ShopPriceTable table;
        try {
            table = ShopPriceTable.fromBytes(data);
        } catch (Exception e) {
            SRE.LOGGER.warn("[ShopPriceCache] failed to parse synced table", e);
            return;
        }
        // 即使哈希异常也先应用，保证当前会话显示正确；仅在哈希一致时才落盘缓存。
        apply(hash, table);
        if (hash != null && SceneAssetCodec.isValidHash(hash) && SceneAssetCodec.sha256(data).equals(hash)) {
            try {
                Files.createDirectories(ROOT);
                Files.write(cachePath(hash), data);
                touch(hash);
                enforceLimit(hash);
            } catch (IOException e) {
                SRE.LOGGER.warn("[ShopPriceCache] failed to persist synced table {}", hash, e);
            }
        }
    }

    private static void apply(String hash, ShopPriceTable table) {
        applied = table;
        appliedHash = hash;
    }

    // ------------------------------------------------------------------
    // 显示价覆盖 / Display-price override
    // ------------------------------------------------------------------

    /**
     * 返回某商店条目应显示的基础价：若已同步且与本地条目（按下标 + itemId 校验）匹配，用同步价；否则用 {@code fallback}。
     * The base price a shop entry should display: the synced price when available and item-id matched
     * by index, else {@code fallback} (the locally-built entry's own price).
     */
    public static int overrideBasePrice(int index, ItemStack stack, int fallback) {
        ShopPriceTable table = applied;
        if (table == null || stack == null) {
            return fallback;
        }
        Minecraft mc = Minecraft.getInstance();
        var gameComponent = SREClient.gameComponent;
        if (mc.player == null || gameComponent == null) {
            return fallback;
        }
        var role = gameComponent.getRole(mc.player);
        if (role == null) {
            return fallback;
        }
        List<ShopPriceTable.Entry> entries = table.get(role.getIdentifier().toString());
        if (entries == null || index < 0 || index >= entries.size()) {
            return fallback;
        }
        ShopPriceTable.Entry entry = entries.get(index);
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!entry.itemId().equals(itemId)) {
            // 下标错位（结构不一致）：回退到本地价，避免显示错误的价格。
            return fallback;
        }
        return entry.price();
    }

    // ------------------------------------------------------------------
    // 本地文件 / LRU / Local files & LRU
    // ------------------------------------------------------------------

    private static Path cachePath(String hash) {
        return ROOT.resolve(hash + ".dat");
    }

    private static void touch(String hash) {
        index.entries.put(hash, System.currentTimeMillis());
        saveIndex();
    }

    /** 超过上限时，淘汰除当前哈希外最久未访问的文件，直到不超过 {@link #MAX_FILES}。 */
    private static void enforceLimit(String keepHash) {
        if (index.entries.size() <= MAX_FILES) {
            return;
        }
        List<Map.Entry<String, Long>> candidates = new ArrayList<>(index.entries.entrySet());
        candidates.removeIf(e -> e.getKey().equals(keepHash));
        candidates.sort(Comparator.comparingLong(Map.Entry::getValue)); // oldest first
        for (Map.Entry<String, Long> e : candidates) {
            if (index.entries.size() <= MAX_FILES) {
                break;
            }
            try {
                Files.deleteIfExists(cachePath(e.getKey()));
            } catch (IOException ignored) {
            }
            index.entries.remove(e.getKey());
        }
        saveIndex();
    }

    private static CacheIndex loadIndex() {
        try {
            if (Files.isRegularFile(INDEX_FILE)) {
                CacheIndex loaded = GSON.fromJson(Files.readString(INDEX_FILE), CacheIndex.class);
                if (loaded != null && loaded.entries != null) {
                    return loaded;
                }
            }
        } catch (Exception e) {
            SRE.LOGGER.warn("[ShopPriceCache] failed to load index", e);
        }
        return new CacheIndex();
    }

    private static void saveIndex() {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(INDEX_FILE, GSON.toJson(index));
        } catch (IOException e) {
            SRE.LOGGER.warn("[ShopPriceCache] failed to save index", e);
        }
    }
}

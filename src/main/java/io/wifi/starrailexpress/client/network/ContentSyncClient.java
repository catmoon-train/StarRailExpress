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

package io.wifi.starrailexpress.client.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.synccontent.ContentChannel;
import io.wifi.starrailexpress.synccontent.ContentDataS2CPayload;
import io.wifi.starrailexpress.synccontent.ContentHandshakeS2CPayload;
import io.wifi.starrailexpress.synccontent.ContentRequestC2SPayload;
import io.wifi.starrailexpress.synccontent.SyncCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 自定义内容同步的客户端：**本地磁盘缓存 + 按需请求**。
 *
 * <p>
 * 收到握手后：本地已有同哈希缓存 → 直接应用（服务端一个字节都不用发）；没有 → 向服务端请求
 * 完整内容。收到内容后解压、校验哈希、应用到对应系统，并把内容按哈希写进本地缓存
 * （{@code config/sre_sync_cache/<通道>/}，每通道最多 {@link #MAX_CACHED_PER_CHANNEL} 份，
 * 按最近使用时间淘汰）——所以「再次加入同一台配置未变的服务器」是完全零流量的。
 *
 * <p>
 * 每个系统通过 {@link #registerChannel} 注册自己的应用回调（回调里做的事与旧的
 * {@code XxxClientNetwork} 一致：落盘 config 目录副本 + 重载本地索引）。
 *
 * <p>
 * 断开连接时调用 {@link #clearSession()}：只清「本次会话已应用」的记录与内存态，
 * **保留磁盘缓存**（这正是下次零流量的前提）。
 */
@Environment(EnvType.CLIENT)
public final class ContentSyncClient {

    /** 每个通道在本地保留的缓存份数（不同服务器配置不同，故保留多份）。 */
    public static final int MAX_CACHED_PER_CHANNEL = 5;

    /** 单包压缩数据上限（与服务端 {@code ContentSyncServer.MAX_CHUNK_BYTES} 对应）。 */
    public static final int MAX_CHUNK_BYTES = 512 * 1024;

    /** 把某个通道的完整 JSON 应用到本地系统。 */
    public interface Applier {
        void apply(String json);
    }

    private static final Path CACHE_ROOT = FabricLoader.getInstance().getConfigDir().resolve("sre_sync_cache");

    private static final Map<ContentChannel, Applier> APPLIERS = new EnumMap<>(ContentChannel.class);
    /** 本次会话已应用的哈希（含空内容的 ""）。 */
    private static final Map<ContentChannel, String> APPLIED_HASHES = new EnumMap<>(ContentChannel.class);
    /** 本次会话已应用的内容（供各系统查询，等价于旧的 getSyncedJson()）。 */
    private static final Map<ContentChannel, String> APPLIED_JSON = new EnumMap<>(ContentChannel.class);
    /** 正在接收的分块：key = 通道 + 哈希。 */
    private static final Map<String, Assembly> ASSEMBLIES = new HashMap<>();

    private ContentSyncClient() {
    }

    /** 分块接收状态。 */
    private static final class Assembly {
        private final byte[][] chunks;
        private int received;

        private Assembly(int totalChunks) {
            this.chunks = new byte[Math.max(1, totalChunks)][];
        }
    }

    // ==================== 注册 ====================

    /** 注册三个同步包的接收器（客户端初始化时调用一次）。 */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ContentHandshakeS2CPayload.TYPE, (payload, context) ->
                context.client().execute(() -> handleHandshake(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ContentDataS2CPayload.TYPE, (payload, context) ->
                context.client().execute(() -> handleData(payload)));
    }

    /** 注册某通道的应用回调（各系统的客户端网络类在自己 register() 里调用）。 */
    public static void registerChannel(ContentChannel channel, Applier applier) {
        APPLIERS.put(channel, applier);
    }

    // ==================== 握手 / 数据 ====================

    private static void handleHandshake(ContentHandshakeS2CPayload payload) {
        if (payload.protocolVersion() != ContentChannel.PROTOCOL_VERSION) {
            SRE.LOGGER.warn("[ContentSync] Protocol version mismatch: server {} / client {}",
                    payload.protocolVersion(), ContentChannel.PROTOCOL_VERSION);
        }
        for (Map.Entry<String, String> entry : payload.hashes().entrySet()) {
            ContentChannel channel = ContentChannel.byId(entry.getKey());
            if (channel == null || !APPLIERS.containsKey(channel)) {
                continue;
            }
            String hash = entry.getValue();
            if (hash == null || hash.isEmpty()) {
                // 服务端该通道为空：本地清空，无需回请求。
                if (!"".equals(APPLIED_HASHES.get(channel))) {
                    apply(channel, "", "");
                }
                continue;
            }
            if (hash.equals(APPLIED_HASHES.get(channel))) {
                continue;
            }
            String cached = readCache(channel, hash);
            if (cached != null) {
                apply(channel, hash, cached);
                continue;
            }
            ClientPlayNetworking.send(new ContentRequestC2SPayload(channel.id(), hash));
        }
    }

    private static void handleData(ContentDataS2CPayload payload) {
        ContentChannel channel = ContentChannel.byId(payload.channel());
        if (channel == null || !APPLIERS.containsKey(channel)) {
            return;
        }
        String hash = payload.hash();
        if (hash == null || hash.isEmpty() || payload.totalChunks() <= 1) {
            // 单包（含空内容）：直接解压应用。
            String json = inflate(payload.data());
            if (json != null) {
                apply(channel, hash == null ? "" : hash, json, true);
            }
            return;
        }
        String key = channel.id() + "@" + hash;
        Assembly assembly = ASSEMBLIES.get(key);
        if (assembly == null || assembly.chunks.length != payload.totalChunks()) {
            assembly = new Assembly(payload.totalChunks());
            ASSEMBLIES.put(key, assembly);
        }
        int index = payload.chunkIndex();
        if (index < 0 || index >= assembly.chunks.length || assembly.chunks[index] != null) {
            return;
        }
        assembly.chunks[index] = payload.data();
        assembly.received++;
        if (assembly.received < assembly.chunks.length) {
            return;
        }
        ASSEMBLIES.remove(key);
        int total = 0;
        for (byte[] chunk : assembly.chunks) {
            total += chunk.length;
        }
        byte[] merged = new byte[total];
        int offset = 0;
        for (byte[] chunk : assembly.chunks) {
            System.arraycopy(chunk, 0, merged, offset, chunk.length);
            offset += chunk.length;
        }
        String json = inflate(merged);
        if (json != null) {
            apply(channel, hash, json, true);
        }
    }

    @Nullable
    private static String inflate(byte[] data) {
        try {
            return SyncCodec.inflateToString(data);
        } catch (IOException e) {
            SRE.LOGGER.error("[ContentSync] Failed to decompress synced content", e);
            return null;
        }
    }

    // ==================== 应用 ====================

    private static void apply(ContentChannel channel, String hash, String json) {
        apply(channel, hash, json, false);
    }

    /**
     * 应用内容并记录哈希。
     *
     * @param persist 是否写入本地缓存（来自网络的新内容才需要；命中缓存时已经有了）
     */
    private static void apply(ContentChannel channel, String hash, String json, boolean persist) {
        Applier applier = APPLIERS.get(channel);
        if (applier == null) {
            return;
        }
        try {
            applier.apply(json == null ? "" : json);
        } catch (Exception e) {
            SRE.LOGGER.error("[ContentSync] Failed to apply synced content for {}", channel.id(), e);
            return;
        }
        APPLIED_HASHES.put(channel, hash == null ? "" : hash);
        APPLIED_JSON.put(channel, json == null ? "" : json);
        if (persist && hash != null && !hash.isEmpty()) {
            writeCache(channel, hash, json);
        }
    }

    /** 本次会话该通道已应用的内容（未同步返回 null）。 */
    @Nullable
    public static String appliedJson(ContentChannel channel) {
        return APPLIED_JSON.get(channel);
    }

    /** 本次会话该通道已应用的哈希（未同步返回 null）。 */
    @Nullable
    public static String appliedHash(ContentChannel channel) {
        return APPLIED_HASHES.get(channel);
    }

    // ==================== 本地缓存 ====================

    private static Path channelCacheDir(ContentChannel channel) {
        return CACHE_ROOT.resolve(channel.id());
    }

    private static Path cacheFile(ContentChannel channel, String hash) {
        return channelCacheDir(channel).resolve(hash + ".json");
    }

    /** 读缓存并校验哈希（文件损坏 / 不是同一份内容都按未命中处理）。 */
    @Nullable
    private static String readCache(ContentChannel channel, String hash) {
        Path file = cacheFile(channel, hash);
        if (!SyncCodec.isValidHash(hash) || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (!SyncCodec.hash(channel, json).equals(hash)) {
                Files.deleteIfExists(file);
                return null;
            }
            touch(file);
            return json;
        } catch (IOException e) {
            SRE.LOGGER.warn("[ContentSync] Failed to read cached content {}", file, e);
            return null;
        }
    }

    private static void writeCache(ContentChannel channel, String hash, String json) {
        try {
            Path dir = channelCacheDir(channel);
            Files.createDirectories(dir);
            Path file = cacheFile(channel, hash);
            Files.writeString(file, json, StandardCharsets.UTF_8);
            touch(file);
            enforceLimit(dir);
        } catch (IOException e) {
            SRE.LOGGER.warn("[ContentSync] Failed to persist synced content for {}", channel.id(), e);
        }
    }

    private static void touch(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException ignored) {
        }
    }

    /** 超过上限时按最近使用时间淘汰最旧的缓存。 */
    private static void enforceLimit(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            if (files.size() <= MAX_CACHED_PER_CHANNEL) {
                return;
            }
            files.stream()
                    .sorted(Comparator.comparingLong(ContentSyncClient::lastModified))
                    .limit(files.size() - MAX_CACHED_PER_CHANNEL)
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            SRE.LOGGER.warn("[ContentSync] Failed to clean cache dir {}", dir, e);
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    // ==================== 会话清理 ====================

    /**
     * 断开连接 / 离开世界时调用：清掉「本次会话已应用」的记录与未接收完的分块，
     * 磁盘缓存保留（下次加入同一台服务器可以直接命中）。
     */
    public static void clearSession() {
        APPLIED_HASHES.clear();
        APPLIED_JSON.clear();
        ASSEMBLIES.clear();
    }

    /** 清掉全部本地缓存（调试 / 排障用）。 */
    public static void clearDiskCache() {
        try {
            if (!Files.isDirectory(CACHE_ROOT)) {
                return;
            }
            try (Stream<Path> stream = Files.walk(CACHE_ROOT)) {
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        } catch (IOException e) {
            SRE.LOGGER.warn("[ContentSync] Failed to clear disk cache", e);
        }
    }
}

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

package io.wifi.starrailexpress.synccontent;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义内容同步的服务端协调器：**先发哈希，按需发全文**。
 *
 * <p>
 * 协议：
 * <ol>
 * <li>玩家加入 / 配置重载 → 服务端发一个 {@link ContentHandshakeS2CPayload}（全部通道的哈希，几十字节）；</li>
 * <li>客户端本地缓存命中 → 什么都不用发；未命中 → 回一个
 * {@link ContentRequestC2SPayload}；</li>
 * <li>服务端只在收到请求时把该通道的内容 deflate 压缩后下发
 * （{@link ContentDataS2CPayload}，压缩后超过 {@link #MAX_CHUNK_BYTES} 才分块）。</li>
 * </ol>
 *
 * <p>
 * 相比旧的「每个系统各自把 JSON 切成 30000 字符的分块无条件推给每个玩家」：
 * 加入服务器从「4 份全文 × N 个分块」降到 1 个小包；配置没变的客户端一个字节都不发。
 *
 * <p>
 * 通道内容为空的服务器，握手发空哈希（{@code ""}），客户端直接本地清空、不回请求，
 * 因此「服务端没有自定义内容」这种情况也是零额外流量。
 */
public final class ContentSyncServer {

    /** 单包压缩数据上限（原版自定义载荷单包上限 1 MiB，留足余量）。 */
    public static final int MAX_CHUNK_BYTES = 512 * 1024;

    /** 单个通道压缩后的合理上限，超过视为配置异常，拒绝下发。 */
    public static final int MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;

    /** 通道 → 已缓存的（内容, 哈希）。配置重载时清空。 */
    private static final Map<ContentChannel, CachedContent> CACHE = new EnumMap<>(ContentChannel.class);

    /** 上一次广播的握手与其所在刻，用于去掉同一刻内内容相同的重复广播。 */
    private static ContentHandshakeS2CPayload lastBroadcast;
    private static long lastBroadcastTick = Long.MIN_VALUE;

    private record CachedContent(String json, String hash) {
    }

    private ContentSyncServer() {
    }

    // ==================== 缓存 ====================

    /** 配置重载后调用：丢弃该通道的缓存，下次握手重新读文件算哈希。 */
    public static synchronized void invalidate(ContentChannel channel) {
        CACHE.remove(channel);
    }

    /** 配置重载后调用：丢弃全部通道缓存。 */
    public static synchronized void invalidateAll() {
        CACHE.clear();
    }

    private static synchronized CachedContent contentOf(ContentChannel channel, MinecraftServer server) {
        CachedContent cached = CACHE.get(channel);
        if (cached != null) {
            return cached;
        }
        String json = readConfig(server, channel);
        CachedContent fresh = new CachedContent(json, json.isEmpty() ? "" : SyncCodec.hash(channel, json));
        CACHE.put(channel, fresh);
        return fresh;
    }

    /** 读该通道在服务端存档里的配置文件（不存在 / 读取失败都当作空内容）。 */
    private static String readConfig(MinecraftServer server, ContentChannel channel) {
        if (server == null) {
            return "";
        }
        try {
            Path path = server.getWorldPath(LevelResource.ROOT).resolve(channel.fileName());
            if (!Files.isRegularFile(path)) {
                return "";
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            SRE.LOGGER.error("[ContentSync] Failed to read {}", channel.fileName(), e);
            return "";
        }
    }

    // ==================== 握手 ====================

    /** 给单个玩家发握手（玩家加入时调用）。 */
    public static void handshakeTo(ServerPlayer player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerPlayNetworking.send(player, buildHandshake(server));
    }

    /** 给所有在线玩家重新握手（配置重载后调用：只有内容变了的客户端才会回请求）。 */
    public static void broadcastHandshake(MinecraftServer server) {
        if (server == null) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            // 没人在线：不必读配置算哈希，玩家进服时的 handshakeTo 会做同样的事
            return;
        }
        ContentHandshakeS2CPayload payload = buildHandshake(server);
        long tick = server.getTickCount();
        if (tick == lastBroadcastTick && payload.equals(lastBroadcast)) {
            // 握手包本来就带全部通道的哈希：同一刻内后几次广播（如 /sre:reload 依次重载四个类型）
            // 内容完全一样，只发一次
            return;
        }
        lastBroadcast = payload;
        lastBroadcastTick = tick;
        for (ServerPlayer player : players) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static ContentHandshakeS2CPayload buildHandshake(MinecraftServer server) {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (ContentChannel channel : ContentChannel.values()) {
            hashes.put(channel.id(), contentOf(channel, server).hash());
        }
        return new ContentHandshakeS2CPayload(ContentChannel.PROTOCOL_VERSION, hashes);
    }

    // ==================== 按需下发 ====================

    /**
     * 处理客户端的完整内容请求。
     *
     * <p>
     * 请求里带的哈希与当前内容不一致（客户端拿的是旧哈希）时也照发——客户端会以内容为准，
     * 这样不会因为一次竞态把客户端卡在旧数据上。
     */
    public static void serve(ServerPlayer player, String channelId, String requestedHash) {
        if (player == null) {
            return;
        }
        ContentChannel channel = ContentChannel.byId(channelId);
        if (channel == null) {
            SRE.LOGGER.warn("[ContentSync] Unknown channel requested: {}", channelId);
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CachedContent content = contentOf(channel, server);
        String json = content.json();
        if (json.isEmpty()) {
            // 内容为空：服务端已知哈希为 ""，客户端不会来请求；走到了也直接回一个空块。
            ServerPlayNetworking.send(player, new ContentDataS2CPayload(channel.id(), "", 0, 1, new byte[0]));
            return;
        }
        byte[] compressed;
        try {
            compressed = SyncCodec.deflate(json);
        } catch (IOException e) {
            SRE.LOGGER.error("[ContentSync] Failed to compress {} for {}", channel.fileName(), player.getName().getString(), e);
            return;
        }
        if (compressed.length > MAX_COMPRESSED_BYTES) {
            SRE.LOGGER.error("[ContentSync] {} is too large after compression ({} bytes), refused",
                    channel.fileName(), compressed.length);
            return;
        }
        int totalChunks = Math.max(1, (compressed.length + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES);
        for (int i = 0; i < totalChunks; i++) {
            int from = i * MAX_CHUNK_BYTES;
            int to = Math.min(from + MAX_CHUNK_BYTES, compressed.length);
            byte[] part = Arrays.copyOfRange(compressed, from, to);
            ServerPlayNetworking.send(player,
                    new ContentDataS2CPayload(channel.id(), content.hash(), i, totalChunks, part));
        }
        SRE.LOGGER.debug("[ContentSync] Sent {} ({} bytes json / {} bytes deflate / {} chunk(s)) to {}",
                channel.id(), json.length(), compressed.length, totalChunks, player.getName().getString());
    }
}

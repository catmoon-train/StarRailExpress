package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义职业服务端网络处理
 * 服务端发送自定义职业 JSON 给客户端，按玩家跟踪 hash 避免重复发送
 * 大 JSON 自动分块传输，避免超过 Minecraft writeUtf 的 32767 字节限制
 */
public class CustomRoleServerNetwork {

    private static String cachedJsonContent = null;
    private static int cachedHash = 0;
    private static boolean hasCustomRoles = false;
    // 记录每个玩家最后一次收到的 hash，避免重复发送相同内容
    private static final Map<UUID, Integer> playerHashCache = new ConcurrentHashMap<>();

    /**
     * 同步给所有在线玩家（仅在配置变更后调用）
     */
    public static void syncToAllPlayers(MinecraftServer server) {
        loadConfigContent(server);
        if (!hasCustomRoles) return;

        int currentHash = cachedHash;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendChunked(player, currentHash, cachedJsonContent);
        }
    }

    /**
     * 发送给新加入的玩家
     */
    public static void syncToPlayer(MinecraftServer server, ServerPlayer player) {
        loadConfigContent(server);
        if (!hasCustomRoles) return;

        sendChunked(player, cachedHash, cachedJsonContent);
    }

    /**
     * 分块发送 JSON 内容，当内容超过单包限制时自动拆分
     */
    private static void sendChunked(ServerPlayer player, int hash, String fullContent) {
        // hash 相同则跳过
        Integer lastHash = playerHashCache.get(player.getUUID());
        if (lastHash != null && lastHash == hash) {
            return;
        }

        int totalLength = fullContent.length();
        int maxChunkChars = CustomRoleSyncPayload.MAX_CHUNK_CHARS;
        int totalChunks = (totalLength + maxChunkChars - 1) / maxChunkChars;

        for (int i = 0; i < totalChunks; i++) {
            int start = i * maxChunkChars;
            int end = Math.min(start + maxChunkChars, totalLength);
            String chunk = fullContent.substring(start, end);
            ServerPlayNetworking.send(player, new CustomRoleSyncPayload(hash, totalChunks, i, chunk));
        }

        playerHashCache.put(player.getUUID(), hash);
    }

    /**
     * 玩家断开时清理记录
     */
    public static void onPlayerDisconnect(UUID playerId) {
        playerHashCache.remove(playerId);
    }

    /**
     * 读取服务端存档中的 sre_custom_roles.json，计算 hash
     */
    private static void loadConfigContent(MinecraftServer server) {
        if (cachedJsonContent != null) return;
        try {
            Path worldPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            Path configPath = worldPath.resolve("sre_custom_roles.json");
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath, StandardCharsets.UTF_8);
                String trimmed = content.trim();
                if (!trimmed.isEmpty() && !trimmed.equals("{}") && !trimmed.equals("{\"roles\":[]}")) {
                    cachedJsonContent = content;
                    cachedHash = content.hashCode();
                    hasCustomRoles = true;
                    return;
                }
            }
        } catch (IOException e) {
            SRE.LOGGER.error("[CustomRole] Failed to read sre_custom_roles.json for sync", e);
        }
        hasCustomRoles = false;
    }

    /**
     * 清除所有缓存（重载角色配置后调用）
     */
    public static void clearCache() {
        cachedJsonContent = null;
        cachedHash = 0;
        hasCustomRoles = false;
        playerHashCache.clear();
    }
}

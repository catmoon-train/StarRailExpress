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

package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.customitem.CustomItemConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义列车物品服务端网络处理（照抄 {@link CustomRoleServerNetwork}）。
 */
public class CustomItemServerNetwork {

    private static String cachedJsonContent = null;
    private static int cachedHash = 0;
    private static boolean hasCustomItems = false;
    private static final Map<UUID, Integer> playerHashCache = new ConcurrentHashMap<>();

    public static void syncToAllPlayers(MinecraftServer server) {
        loadConfigContent(server);
        if (!hasCustomItems)
            return;
        int currentHash = cachedHash;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendChunked(player, currentHash, cachedJsonContent);
        }
    }

    public static void syncToPlayer(MinecraftServer server, ServerPlayer player) {
        loadConfigContent(server);
        if (!hasCustomItems)
            return;
        sendChunked(player, cachedHash, cachedJsonContent);
    }

    private static void sendChunked(ServerPlayer player, int hash, String fullContent) {
        Integer lastHash = playerHashCache.get(player.getUUID());
        if (lastHash != null && lastHash == hash)
            return;

        int totalLength = fullContent.length();
        int maxChunkChars = CustomItemSyncPayload.MAX_CHUNK_CHARS;
        int totalChunks = (totalLength + maxChunkChars - 1) / maxChunkChars;

        for (int i = 0; i < totalChunks; i++) {
            int start = i * maxChunkChars;
            int end = Math.min(start + maxChunkChars, totalLength);
            String chunk = fullContent.substring(start, end);
            ServerPlayNetworking.send(player, new CustomItemSyncPayload(hash, totalChunks, i, chunk));
        }
        playerHashCache.put(player.getUUID(), hash);
    }

    public static void onPlayerDisconnect(UUID playerId) {
        playerHashCache.remove(playerId);
    }

    private static void loadConfigContent(MinecraftServer server) {
        if (cachedJsonContent != null)
            return;
        try {
            Path worldPath = server.getWorldPath(LevelResource.ROOT);
            Path configPath = worldPath.resolve(CustomItemConfig.FILE_NAME);
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath, StandardCharsets.UTF_8);
                String trimmed = content.trim();
                if (!trimmed.isEmpty() && !trimmed.equals("{}") && !trimmed.equals("{\"items\":[]}")) {
                    cachedJsonContent = content;
                    cachedHash = content.hashCode();
                    hasCustomItems = true;
                    return;
                }
            }
        } catch (IOException e) {
            SRE.LOGGER.error("[CustomItem] Failed to read {} for sync", CustomItemConfig.FILE_NAME, e);
        }
        hasCustomItems = false;
    }

    /** 清除所有缓存（重载配置后调用）。 */
    public static void clearCache() {
        cachedJsonContent = null;
        cachedHash = 0;
        hasCustomItems = false;
        playerHashCache.clear();
    }
}

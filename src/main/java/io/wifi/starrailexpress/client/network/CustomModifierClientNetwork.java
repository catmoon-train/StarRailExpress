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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.wifi.starrailexpress.custommodifier.CustomModifierConfig;
import io.wifi.starrailexpress.custommodifier.CustomModifierData;
import io.wifi.starrailexpress.custommodifier.CustomModifierLoader;
import io.wifi.starrailexpress.network.CustomModifierSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义修饰符客户端网络处理（照抄 {@link CustomRoleClientNetwork}）。
 */
public class CustomModifierClientNetwork {

    private static final Gson GSON = new GsonBuilder().create();

    private static int lastReceivedHash = 0;
    private static String syncedJson = null;
    private static final List<CustomModifierData> syncedModifiers = new ArrayList<>();
    private static boolean hasSyncedData = false;

    // ---- 分块接收状态 ----
    private static int assemblingHash = 0;
    private static int assemblingTotalChunks = 0;
    private static final Map<Integer, String> assemblingChunks = new HashMap<>();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CustomModifierSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> handleChunk(payload.hash(), payload.totalChunks(), payload.chunkIndex(),
                    payload.chunkData()));
        });
    }

    private static void handleChunk(int hash, int totalChunks, int chunkIndex, String chunkData) {
        if (hasSyncedData && hash == lastReceivedHash && totalChunks == 1)
            return;

        if (hash != assemblingHash) {
            assemblingHash = hash;
            assemblingTotalChunks = totalChunks;
            assemblingChunks.clear();
        }
        assemblingChunks.put(chunkIndex, chunkData);

        if (assemblingChunks.size() >= assemblingTotalChunks) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < assemblingTotalChunks; i++) {
                String part = assemblingChunks.get(i);
                if (part == null) {
                    assemblingChunks.clear();
                    return;
                }
                sb.append(part);
            }
            String fullJson = sb.toString();
            assemblingChunks.clear();
            if (fullJson.isEmpty())
                return;

            lastReceivedHash = hash;
            syncedJson = fullJson;
            syncedModifiers.clear();
            try {
                JsonObject root = GSON.fromJson(fullJson, JsonObject.class);
                if (root != null && root.has("modifiers")) {
                    for (var element : root.getAsJsonArray("modifiers")) {
                        CustomModifierData data = GSON.fromJson(element, CustomModifierData.class);
                        if (data != null && data.englishId != null) {
                            syncedModifiers.add(data);
                        }
                    }
                }
                writeToLocalConfig(fullJson);
                CustomModifierLoader.reloadClient();
            } catch (Exception e) {
                e.printStackTrace();
            }
            hasSyncedData = true;
        }
    }

    private static void writeToLocalConfig(String json) {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Files.createDirectories(configDir);
            try (BufferedWriter writer = Files.newBufferedWriter(configDir.resolve(CustomModifierConfig.FILE_NAME),
                    StandardCharsets.UTF_8)) {
                writer.write(json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getSyncedJson() {
        return syncedJson;
    }

    public static List<CustomModifierData> getSyncedModifiers() {
        return new ArrayList<>(syncedModifiers);
    }

    public static CustomModifierData getSyncedModifier(String englishId) {
        for (CustomModifierData data : syncedModifiers) {
            if (data.englishId.equals(englishId))
                return data;
        }
        return null;
    }

    public static boolean hasSyncedData() {
        return hasSyncedData;
    }

    /** 清理缓存（离开服务器时），并把本地文件删除。 */
    public static void clearCache() {
        lastReceivedHash = 0;
        syncedJson = null;
        syncedModifiers.clear();
        hasSyncedData = false;
        assemblingHash = 0;
        assemblingTotalChunks = 0;
        assemblingChunks.clear();

        CustomModifierLoader.removeClientCache();
        try {
            Files.deleteIfExists(FabricLoader.getInstance().getConfigDir().resolve(CustomModifierConfig.FILE_NAME));
        } catch (Exception ignored) {
        }
    }
}

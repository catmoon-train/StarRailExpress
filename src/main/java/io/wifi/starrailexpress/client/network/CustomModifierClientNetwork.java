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
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.custommodifier.CustomModifierConfig;
import io.wifi.starrailexpress.custommodifier.CustomModifierData;
import io.wifi.starrailexpress.custommodifier.CustomModifierLoader;
import io.wifi.starrailexpress.synccontent.ContentChannel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义修饰符客户端处理。
 *
 * <p>
 * 传输由 {@link ContentSyncClient} 统一负责，这里只做「内容到手之后」的解析、落盘与重载。
 */
@Environment(EnvType.CLIENT)
public class CustomModifierClientNetwork {

    private static final Gson GSON = new GsonBuilder().create();

    private static String syncedJson = null;
    private static final List<CustomModifierData> syncedModifiers = new ArrayList<>();

    public static void register() {
        ContentSyncClient.registerChannel(ContentChannel.CUSTOM_MODIFIER, CustomModifierClientNetwork::applyJson);
    }

    /** 应用服务端下发的完整配置内容。 */
    private static void applyJson(String fullJson) {
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
            SRE.LOGGER.error("[CustomModifier-Client] Failed to apply synced content", e);
        }
    }

    private static void writeToLocalConfig(String json) {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve(CustomModifierConfig.FILE_NAME),
                    json == null ? "" : json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            SRE.LOGGER.warn("[CustomModifier-Client] Failed to write local config copy", e);
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
            if (data.englishId.equals(englishId)) {
                return data;
            }
        }
        return null;
    }

    public static boolean hasSyncedData() {
        return syncedJson != null;
    }

    /** 清理缓存（离开服务器时），并删除本地副本（磁盘缓存保留在 ContentSyncClient 里）。 */
    public static void clearCache() {
        syncedJson = null;
        syncedModifiers.clear();
        CustomModifierLoader.removeClientCache();
        try {
            Files.deleteIfExists(FabricLoader.getInstance().getConfigDir().resolve(CustomModifierConfig.FILE_NAME));
        } catch (Exception ignored) {
        }
    }
}

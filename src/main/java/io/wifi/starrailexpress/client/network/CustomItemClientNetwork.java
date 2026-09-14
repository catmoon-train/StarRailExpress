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
import io.wifi.starrailexpress.customitem.CustomItemConfig;
import io.wifi.starrailexpress.customitem.CustomItemData;
import io.wifi.starrailexpress.customitem.CustomItemLoader;
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
 * 自定义列车物品客户端处理。
 *
 * <p>
 * 传输（握手 / 压缩 / 磁盘缓存命中判断）由 {@link ContentSyncClient} 统一负责，
 * 这里只做「内容到手之后」的事：解析成列表、写 config 目录副本、重载本地索引。
 */
@Environment(EnvType.CLIENT)
public class CustomItemClientNetwork {

    private static final Gson GSON = new GsonBuilder().create();

    private static String syncedJson = null;
    private static final List<CustomItemData> syncedItems = new ArrayList<>();

    public static void register() {
        ContentSyncClient.registerChannel(ContentChannel.CUSTOM_ITEM, CustomItemClientNetwork::applyJson);
    }

    /** 应用服务端下发的完整配置内容。 */
    private static void applyJson(String fullJson) {
        syncedJson = fullJson;
        syncedItems.clear();
        try {
            JsonObject root = GSON.fromJson(fullJson, JsonObject.class);
            if (root != null && root.has("items")) {
                for (var element : root.getAsJsonArray("items")) {
                    CustomItemData data = GSON.fromJson(element, CustomItemData.class);
                    if (data != null && data.id != null) {
                        syncedItems.add(data);
                    }
                }
            }
            writeToLocalConfig(fullJson);
            CustomItemLoader.reloadClient();
            io.wifi.starrailexpress.client.render.item.CustomItemRenderer.clearCache();
        } catch (Exception e) {
            SRE.LOGGER.error("[CustomItem-Client] Failed to apply synced content", e);
        }
    }

    private static void writeToLocalConfig(String json) {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve(CustomItemConfig.FILE_NAME),
                    json == null ? "" : json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            SRE.LOGGER.warn("[CustomItem-Client] Failed to write local config copy", e);
        }
    }

    public static String getSyncedJson() {
        return syncedJson;
    }

    public static List<CustomItemData> getSyncedItems() {
        return new ArrayList<>(syncedItems);
    }

    public static CustomItemData getSyncedItem(String id) {
        for (CustomItemData data : syncedItems) {
            if (data.id != null && data.id.equals(id)) {
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
        syncedItems.clear();
        CustomItemLoader.removeClientCache();
        io.wifi.starrailexpress.client.render.item.CustomItemRenderer.clearCache();
        try {
            Files.deleteIfExists(FabricLoader.getInstance().getConfigDir().resolve(CustomItemConfig.FILE_NAME));
        } catch (Exception ignored) {
        }
    }
}

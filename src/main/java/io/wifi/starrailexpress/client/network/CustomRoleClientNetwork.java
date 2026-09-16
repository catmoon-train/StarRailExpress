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
import io.wifi.starrailexpress.customrole.CustomNormalRole;
import io.wifi.starrailexpress.customrole.CustomRoleData;
import io.wifi.starrailexpress.customrole.CustomRoleLoader;
import io.wifi.starrailexpress.synccontent.ContentChannel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义职业客户端处理。
 *
 * <p>
 * 传输（握手 / 压缩下发 / 磁盘缓存命中）由 {@link ContentSyncClient} 统一负责；
 * 这里只做「内容到手之后」的事：解析成列表、写 config 目录副本、重载客户端角色表。
 */
@Environment(EnvType.CLIENT)
public class CustomRoleClientNetwork {

    /** 客户端本地文件名（与 {@code CustomRoleConfig} 保持一致）。 */
    private static final String FILE_NAME = "sre_custom_roles.json";

    private static final Gson GSON = new GsonBuilder().create();

    private static String syncedJson = null;
    private static final List<CustomRoleData> syncedRoles = new ArrayList<>();

    public static void register() {
        ContentSyncClient.registerChannel(ContentChannel.CUSTOM_ROLE, CustomRoleClientNetwork::applyJson);
    }

    /** 应用服务端下发的完整配置内容。 */
    private static void applyJson(String fullJson) {
        syncedJson = fullJson;
        syncedRoles.clear();
        try {
            JsonObject root = GSON.fromJson(fullJson, JsonObject.class);
            if (root != null && root.has("roles")) {
                for (var element : root.getAsJsonArray("roles")) {
                    CustomRoleData data = GSON.fromJson(element, CustomRoleData.class);
                    if (data != null && data.englishId != null) {
                        syncedRoles.add(data);
                    }
                }
            }
            writeToLocalConfig(fullJson);
            // 触发客户端把自定义角色注册进 TMMRoles.ROLES（商店条目也在这一步按 id 解析自定义物品）
            CustomRoleLoader.reloadClient();
            // 商店界面已经打开时，它里面那份控件是打开那一刻按旧条目建的，重注册后要重建一次，
            // 否则「服务端已经重载、商店显示的还是旧的（或空的）」
            refreshOpenShopScreen();
        } catch (Exception e) {
            SRE.LOGGER.error("[CustomRole-Client] Failed to apply synced content", e);
        }
    }

    /** 重建正在打开的角色商店界面（若已打开），让重新注册后的条目立刻可见。 */
    private static void refreshOpenShopScreen() {
        Minecraft client = Minecraft.getInstance();
        if (client != null
                && client.screen instanceof io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen shop) {
            shop.reinit();
        }
    }

    private static void writeToLocalConfig(String json) {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve(FILE_NAME),
                    json == null ? "" : json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            SRE.LOGGER.warn("[CustomRole-Client] Failed to write local config copy", e);
        }
    }

    /** 同步过来的原始 JSON 字符串。 */
    public static String getSyncedJson() {
        return syncedJson;
    }

    /** 同步过来的已解析角色数据。 */
    public static List<CustomRoleData> getSyncedRoles() {
        return new ArrayList<>(syncedRoles);
    }

    /** 按 englishId 查找角色数据。 */
    public static CustomRoleData getSyncedRole(String englishId) {
        for (CustomRoleData data : syncedRoles) {
            if (data.englishId.equals(englishId)) {
                return data;
            }
        }
        return null;
    }

    /** 是否已有同步数据。 */
    public static boolean hasSyncedData() {
        return syncedJson != null;
    }

    /**
     * 清理缓存（离开服务器时）：从 {@code TMMRoles.ROLES} 与 {@code INITIAL_ITEMS_MAP} 移除旧的自定义角色，
     * 并删除本地副本文件。
     *
     * <p>磁盘缓存按哈希保留在 {@link ContentSyncClient} 里，下次加入同一台服务器无需重新下载。
     */
    public static void clearCache() {
        syncedJson = null;
        syncedRoles.clear();
        CustomRoleLoader.removeClientCache();

        var roles = io.wifi.starrailexpress.api.TMMRoles.ROLES;
        var toRemove = new ArrayList<ResourceLocation>();
        for (var entry : roles.entrySet()) {
            if (entry.getValue() instanceof CustomNormalRole || "customrole".equals(entry.getKey().getNamespace())) {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(roles::remove);

        var itemsMap = org.agmas.noellesroles.init.RoleInitialItems.INITIAL_ITEMS_MAP;
        var toRemoveItems = new ArrayList<io.wifi.starrailexpress.api.SRERole>();
        for (var entry : itemsMap.entrySet()) {
            if (entry.getKey() instanceof CustomNormalRole
                    || "customrole".equals(entry.getKey().identifier().getNamespace())) {
                toRemoveItems.add(entry.getKey());
            }
        }
        toRemoveItems.forEach(itemsMap::remove);

        try {
            Files.deleteIfExists(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
        } catch (Exception ignored) {
            // 删除失败也不影响功能
        }
    }
}

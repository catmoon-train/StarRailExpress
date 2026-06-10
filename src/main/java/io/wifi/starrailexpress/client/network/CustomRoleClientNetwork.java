package io.wifi.starrailexpress.client.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.customrole.CustomRoleData;
import io.wifi.starrailexpress.network.CustomRoleSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义职业客户端网络处理
 * 接收服务端发来的自定义职业 JSON 配置，解析后存入内存，同时写入本地 config 目录
 */
public class CustomRoleClientNetwork {

    private static final Gson GSON = new GsonBuilder().create();

    /** 客户端上次收到的 hash，用于跳过重复解析 */
    private static int lastReceivedHash = 0;

    /** 客户端内存中缓存的原始 JSON 字符串 */
    private static String syncedJson = null;

    /** 客户端内存中缓存的已解析角色数据列表 */
    private static final List<CustomRoleData> syncedRoles = new ArrayList<>();

    /** 是否已收到过有效的自定义职业数据 */
    private static boolean hasSyncedData = false;

    /**
     * 注册客户端接收器
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CustomRoleSyncPayload.TYPE, (payload, context) -> {
            int hash = payload.hash();
            // hash 相同则跳过解析（内容未变化）
            if (hasSyncedData && hash == lastReceivedHash) {
                return;
            }
            lastReceivedHash = hash;
            context.client().execute(() -> {
                String json = payload.jsonContent();
                if (json != null && !json.isEmpty()) {
                    syncedJson = json;
                    syncedRoles.clear();
                    try {
                        com.google.gson.JsonObject root = GSON.fromJson(json, com.google.gson.JsonObject.class);
                        if (root.has("roles")) {
                            for (var element : root.getAsJsonArray("roles")) {
                                CustomRoleData data = GSON.fromJson(element, CustomRoleData.class);
                                if (data != null && data.englishId != null) {
                                    syncedRoles.add(data);
                                }
                            }
                        }
                        // 写入客户端本地 config 目录，并触发客户端重载角色到 TMMRoles.ROLES
                        writeToLocalConfig(json);
                        io.wifi.starrailexpress.customrole.CustomRoleLoader.reloadClient();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    hasSyncedData = true;
                }
            });
        });
    }

    /**
     * 将同步的 JSON 写入客户端本地 config 目录
     * 这样 CustomRoleLoader.getCustomRoleData() 在客户端也能通过文件读取到数据
     */
    private static void writeToLocalConfig(String json) {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Path configPath = configDir.resolve("sre_custom_roles.json");
            Files.createDirectories(configDir);
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 获取同步过来的原始 JSON 字符串 */
    public static String getSyncedJson() {
        return syncedJson;
    }

    /** 获取同步过来的已解析角色数据 */
    public static List<CustomRoleData> getSyncedRoles() {
        return new ArrayList<>(syncedRoles);
    }

    /** 根据 englishId 查找角色数据 */
    public static CustomRoleData getSyncedRole(String englishId) {
        for (CustomRoleData data : syncedRoles) {
            if (data.englishId.equals(englishId)) {
                return data;
            }
        }
        return null;
    }

    /** 是否已有同步数据 */
    public static boolean hasSyncedData() {
        return hasSyncedData;
    }

    /** 清理缓存（离开服务器时），同时从 TMMRoles.ROLES 移除旧角色并删除本地文件 */
    public static void clearCache() {
        lastReceivedHash = 0;
        syncedJson = null;
        syncedRoles.clear();
        hasSyncedData = false;

        // 从 TMMRoles.ROLES 中移除所有旧的自定义角色（防止换服后残留）
        var roles = io.wifi.starrailexpress.api.TMMRoles.ROLES;
        var toRemove = new ArrayList<net.minecraft.resources.ResourceLocation>();
        for (var entry : roles.entrySet()) {
            if ("customrole".equals(entry.getKey().getNamespace())) {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(roles::remove);

        // 删除本地缓存文件，确保换服后不会读到旧数据
        deleteLocalConfig();
    }

    /** 删除本地 config 目录中的 sre_custom_roles.json */
    private static void deleteLocalConfig() {
        try {
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve("sre_custom_roles.json");
            Files.deleteIfExists(configPath);
        } catch (Exception e) {
            // 删除失败也不影响功能，下次收到包会覆盖写入
        }
    }
}

package io.wifi.starrailexpress.client.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.wifi.starrailexpress.customrole.CustomNormalRole;
import io.wifi.starrailexpress.customrole.CustomRoleData;
import io.wifi.starrailexpress.network.CustomRoleSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 自定义职业客户端网络处理
 * 接收服务端发来的自定义职业 JSON 配置（支持分块传输），解析后存入内存，同时写入本地 config 目录
 */
public class CustomRoleClientNetwork {

    private static final Gson GSON = new GsonBuilder().create();

    /** 客户端上次完成的 hash，用于跳过重复解析 */
    private static int lastReceivedHash = 0;

    /** 客户端内存中缓存的原始 JSON 字符串 */
    private static String syncedJson = null;

    /** 客户端内存中缓存的已解析角色数据列表 */
    private static final List<CustomRoleData> syncedRoles = new ArrayList<>();

    /** 是否已收到过有效的自定义职业数据 */
    private static boolean hasSyncedData = false;

    // ---- 分块接收状态 ----
    /** 当前正在组装的 hash */
    private static int assemblingHash = 0;
    /** 当前正在组装的期望总块数 */
    private static int assemblingTotalChunks = 0;
    /** 已收到的分块数据，key = chunkIndex */
    private static final Map<Integer, String> assemblingChunks = new HashMap<>();

    /**
     * 注册客户端接收器
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CustomRoleSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                handleChunk(payload.hash(), payload.totalChunks(), payload.chunkIndex(), payload.chunkData());
            });
        });
    }

    /**
     * 处理收到的分块
     */
    private static void handleChunk(int hash, int totalChunks, int chunkIndex, String chunkData) {
        // 如果已完成且 hash 相同，直接跳过
        if (hasSyncedData && hash == lastReceivedHash && totalChunks == 1) {
            return;
        }

        // 新 hash 或新传输开始，重置组装状态
        if (hash != assemblingHash) {
            assemblingHash = hash;
            assemblingTotalChunks = totalChunks;
            assemblingChunks.clear();
        }

        assemblingChunks.put(chunkIndex, chunkData);

        // 检查是否收集完所有分块
        if (assemblingChunks.size() >= assemblingTotalChunks) {
            // 按 chunkIndex 排序拼接
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < assemblingTotalChunks; i++) {
                String part = assemblingChunks.get(i);
                if (part == null) {
                    // 缺少某块，等待重发（简单处理：清空并丢弃）
                    assemblingChunks.clear();
                    return;
                }
                sb.append(part);
            }
            String fullJson = sb.toString();

            // 清理组装状态
            assemblingChunks.clear();

            if (fullJson.isEmpty())
                return;

            lastReceivedHash = hash;
            syncedJson = fullJson;
            syncedRoles.clear();
            try {
                com.google.gson.JsonObject root = GSON.fromJson(fullJson, com.google.gson.JsonObject.class);
                if (root.has("roles")) {
                    for (var element : root.getAsJsonArray("roles")) {
                        CustomRoleData data = GSON.fromJson(element, CustomRoleData.class);
                        if (data != null && data.englishId != null) {
                            syncedRoles.add(data);
                        }
                    }
                }
                // 写入客户端本地 config 目录，并触发客户端重载角色到 TMMRoles.ROLES
                writeToLocalConfig(fullJson);
                io.wifi.starrailexpress.customrole.CustomRoleLoader.reloadClient();
            } catch (Exception e) {
                e.printStackTrace();
            }
            hasSyncedData = true;
        }
    }

    /**
     * 将同步的 JSON 写入客户端本地 config 目录
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

    /** 清理缓存（离开服务器时），同时从 TMMRoles.ROLES 和 INITIAL_ITEMS_MAP 移除旧角色并删除本地文件 */
    public static void clearCache() {
        lastReceivedHash = 0;
        syncedJson = null;
        syncedRoles.clear();
        hasSyncedData = false;

        // 清理分块组装状态
        assemblingHash = 0;
        assemblingTotalChunks = 0;
        assemblingChunks.clear();

        // 从 TMMRoles.ROLES 中移除所有旧的自定义角色
        var roles = io.wifi.starrailexpress.api.TMMRoles.ROLES;
        var toRemove = new ArrayList<net.minecraft.resources.ResourceLocation>();
        for (var entry : roles.entrySet()) {
            if (entry.getValue() instanceof CustomNormalRole || "customrole".equals(entry.getKey().getNamespace())) {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(roles::remove);

        // 从 INITIAL_ITEMS_MAP 中移除旧的自定义角色条目
        var itemsMap = org.agmas.noellesroles.init.RoleInitialItems.INITIAL_ITEMS_MAP;
        var toRemoveItems = new ArrayList<io.wifi.starrailexpress.api.SRERole>();
        for (var entry : itemsMap.entrySet()) {
            if (entry.getKey() instanceof CustomNormalRole
                    || "customrole".equals(entry.getKey().identifier().getNamespace())) {
                toRemoveItems.add(entry.getKey());
            }
        }
        toRemoveItems.forEach(itemsMap::remove);

        deleteLocalConfig();
    }

    /** 删除本地 config 目录中的 sre_custom_roles.json */
    private static void deleteLocalConfig() {
        try {
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve("sre_custom_roles.json");
            Files.deleteIfExists(configPath);
        } catch (Exception e) {
            // 删除失败也不影响功能
        }
    }
}

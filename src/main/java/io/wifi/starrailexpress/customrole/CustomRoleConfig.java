package io.wifi.starrailexpress.customrole;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义职业配置文件包装类
 */
public class CustomRoleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "sre_custom_roles.json";

    @SerializedName("roles")
    public List<CustomRoleData> roles = new ArrayList<>();

    private static CustomRoleConfig instance;

    public static CustomRoleConfig getInstance() {
        if (instance == null) {
            instance = new CustomRoleConfig();
            instance.roles = new ArrayList<>();
        }
        return instance;
    }

    /** 获取配置文件保存目录（始终使用Fabric config目录确保一致性） */
    public static Path getSaveDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static Path getConfigPath() {
        return getSaveDir().resolve(FILE_NAME);
    }

    /**
     * 从指定路径加载配置
     */
    public static CustomRoleConfig loadFromFile(Path worldPath) {
        Path configPath = worldPath.resolve(FILE_NAME);
        return loadFromPath(configPath);
    }

    private static CustomRoleConfig loadFromPath(Path configPath) {
        if (Files.exists(configPath)) {
            try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                CustomRoleConfig loaded = GSON.fromJson(reader, CustomRoleConfig.class);
                if (loaded != null && loaded.roles != null) {
                    instance = loaded;
                    return instance;
                }
            } catch (Exception e) {
                System.err.println("[CustomRole] Failed to load: " + e.getMessage());
            }
        }
        instance = new CustomRoleConfig();
        instance.roles = new ArrayList<>();
        return instance;
    }

    /**
     * 保存配置到存档目录
     */
    public void saveToFile(Path worldPath) {
        Path configPath = worldPath.resolve(FILE_NAME);
        saveToPath(configPath);
    }

    private void saveToPath(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            System.err.println("[CustomRole] Failed to save: " + e.getMessage());
        }
    }

    /** 保存到默认路径（存档目录或config目录） */
    public void saveToDefaultPath() {
        saveToPath(getConfigPath());
    }

    /** 从默认路径加载 */
    public static CustomRoleConfig loadFromDefaultPath() {
        Path configPath = getConfigPath();
        return loadFromPath(configPath);
    }

    /** 一律从存档文件读取配置 */
    public static CustomRoleConfig loadPreferWorldPath(MinecraftServer server) {
        // 优先使用 server 直接获取当前 world 存档路径
        if (server != null) {
            try {
                java.nio.file.Path worldPath = server.getWorldPath(LevelResource.ROOT);
                CustomRoleConfig cfg = loadFromFile(worldPath);
                if (cfg != null && cfg.roles != null && !cfg.roles.isEmpty()) return cfg;
            } catch (Exception ignored) {}
        }
        // server 为 null 或 world 路径无配置时，遍历 saves 目录查找
        try {
            java.nio.file.Path savesDir = FabricLoader.getInstance().getGameDir().resolve("saves");
            if (java.nio.file.Files.exists(savesDir) && java.nio.file.Files.isDirectory(savesDir)) {
                try (var stream = java.nio.file.Files.list(savesDir)) {
                    for (java.nio.file.Path worldDir : stream.toList()) {
                        if (!java.nio.file.Files.isDirectory(worldDir)) continue;
                        CustomRoleConfig cfg = loadFromFile(worldDir);
                        if (cfg != null && cfg.roles != null && !cfg.roles.isEmpty()) return cfg;
                    }
                }
            }
        } catch (Exception ignored) {}
        // 存档中找不到任何配置，返回空实例（不回退到 config 目录）
        return getInstance();
    }

    public void addRole(CustomRoleData role) {
        roles.add(role);
    }

    public void removeRole(String englishId) {
        roles.removeIf(r -> r.englishId.equals(englishId));
    }

    public CustomRoleData findRole(String englishId) {
        return roles.stream().filter(r -> r.englishId.equals(englishId)).findFirst().orElse(null);
    }

    /**
     * 将配置保存到 world 存档目录（不从 config 目录回退）
     */
    public void savePreferWorldPath(MinecraftServer server) {
        if (server != null) {
            try {
                java.nio.file.Path worldPath = server.getWorldPath(LevelResource.ROOT);
                saveToFile(worldPath);
                return;
            } catch (Exception ignored) {}
        }
        // server 为 null 时不保存
    }
}

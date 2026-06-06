package io.wifi.starrailexpress.customrole;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

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

    /** 优先从 world 存档路径加载配置，失败则从默认 config 加载 */
    public static CustomRoleConfig loadPreferWorldPath(MinecraftServer server) {
        if (server != null) {
            try {
                java.nio.file.Path worldPath = server.getWorldPath(LevelResource.ROOT);
                CustomRoleConfig cfg = loadFromFile(worldPath);
                if (cfg != null && cfg.roles != null && !cfg.roles.isEmpty()) return cfg;
            } catch (Exception ignored) {}
        }
        return loadFromDefaultPath();
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
     * 优先尝试将配置保存到指定世界存档目录；若 server 为 null 或保存失败则回退到默认 config 目录
     */
    public void savePreferWorldPath(MinecraftServer server) {
        try {
            if (server != null) {
                java.nio.file.Path worldPath = server.getWorldPath(LevelResource.ROOT);
                saveToFile(worldPath);
                return;
            }
        } catch (Exception ignored) {}
        saveToDefaultPath();
    }
}

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

package io.wifi.starrailexpress.custommodifier;

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
 * 自定义修饰符配置文件包装类（独立文件：{@code sre_custom_modifiers.json}）。
 *
 * <p>
 * 结构与 {@link io.wifi.starrailexpress.customrole.CustomRoleConfig} 完全对齐：
 * 权威数据在服务端世界存档，客户端 config 目录只是同步副本。
 */
public class CustomModifierConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String FILE_NAME = "sre_custom_modifiers.json";

    @SerializedName("modifiers")
    public List<CustomModifierData> modifiers = new ArrayList<>();

    private static CustomModifierConfig instance;

    public static CustomModifierConfig getInstance() {
        if (instance == null) {
            instance = new CustomModifierConfig();
            instance.modifiers = new ArrayList<>();
        }
        return instance;
    }

    public static Path getSaveDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static Path getConfigPath() {
        return getSaveDir().resolve(FILE_NAME);
    }

    public static CustomModifierConfig loadFromFile(Path worldPath) {
        return loadFromPath(worldPath.resolve(FILE_NAME));
    }

    private static CustomModifierConfig loadFromPath(Path configPath) {
        if (Files.exists(configPath)) {
            try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                CustomModifierConfig loaded = GSON.fromJson(reader, CustomModifierConfig.class);
                if (loaded != null && loaded.modifiers != null) {
                    instance = loaded;
                    return instance;
                }
            } catch (Exception e) {
                System.err.println("[CustomModifier] Failed to load: " + e.getMessage());
            }
        }
        instance = new CustomModifierConfig();
        instance.modifiers = new ArrayList<>();
        return instance;
    }

    public void saveToFile(Path worldPath) {
        saveToPath(worldPath.resolve(FILE_NAME));
    }

    private void saveToPath(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            System.err.println("[CustomModifier] Failed to save: " + e.getMessage());
        }
    }

    public void saveToDefaultPath() {
        saveToPath(getConfigPath());
    }

    public static CustomModifierConfig loadFromDefaultPath() {
        return loadFromPath(getConfigPath());
    }

    /** 一律从存档文件读取配置（与自定义职业保持一致）。 */
    public static CustomModifierConfig loadPreferWorldPath(MinecraftServer server) {
        if (server != null) {
            try {
                Path worldPath = server.getWorldPath(LevelResource.ROOT);
                CustomModifierConfig cfg = loadFromFile(worldPath);
                if (cfg != null && cfg.modifiers != null && !cfg.modifiers.isEmpty())
                    return cfg;
            } catch (Exception ignored) {
            }
        }
        try {
            Path savesDir = FabricLoader.getInstance().getGameDir().resolve("saves");
            if (Files.exists(savesDir) && Files.isDirectory(savesDir)) {
                try (var stream = Files.list(savesDir)) {
                    for (Path worldDir : stream.toList()) {
                        if (!Files.isDirectory(worldDir))
                            continue;
                        CustomModifierConfig cfg = loadFromFile(worldDir);
                        if (cfg != null && cfg.modifiers != null && !cfg.modifiers.isEmpty())
                            return cfg;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return getInstance();
    }

    public void addModifier(CustomModifierData data) {
        modifiers.add(data);
    }

    public void removeModifier(String englishId) {
        modifiers.removeIf(m -> m.englishId.equals(englishId));
    }

    public CustomModifierData findModifier(String englishId) {
        return modifiers.stream().filter(m -> m.englishId.equals(englishId)).findFirst().orElse(null);
    }

    /** 保存到 world 存档目录（server 为 null 时不保存）。 */
    public void savePreferWorldPath(MinecraftServer server) {
        if (server != null) {
            try {
                saveToFile(server.getWorldPath(LevelResource.ROOT));
            } catch (Exception ignored) {
            }
        }
    }
}

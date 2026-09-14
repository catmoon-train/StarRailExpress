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

package io.wifi.starrailexpress.customblock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import io.wifi.starrailexpress.SRE;
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
 * 自定义方块配置文件包装类（独立文件：{@code sre_custom_blocks.json}）。
 *
 * <p>
 * 结构与 {@link io.wifi.starrailexpress.customitem.CustomItemConfig} 完全对齐：
 * 权威数据在服务端世界存档，客户端 config 目录只是同步副本。
 */
public class CustomBlockConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String FILE_NAME = "sre_custom_blocks.json";

    @SerializedName("blocks")
    public List<CustomBlockData> blocks = new ArrayList<>();

    private static CustomBlockConfig instance;

    public static CustomBlockConfig getInstance() {
        if (instance == null) {
            instance = new CustomBlockConfig();
            instance.blocks = new ArrayList<>();
        }
        return instance;
    }

    public static Path getSaveDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static Path getConfigPath() {
        return getSaveDir().resolve(FILE_NAME);
    }

    public static CustomBlockConfig loadFromFile(Path worldPath) {
        return loadFromPath(worldPath.resolve(FILE_NAME));
    }

    private static CustomBlockConfig loadFromPath(Path configPath) {
        if (Files.exists(configPath)) {
            try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                CustomBlockConfig loaded = GSON.fromJson(reader, CustomBlockConfig.class);
                if (loaded != null && loaded.blocks != null) {
                    instance = loaded;
                    for (CustomBlockData data : instance.blocks) {
                        if (data != null) {
                            data.sanitize();
                        }
                    }
                    return instance;
                }
            } catch (Exception e) {
                SRE.LOGGER.error("[CustomBlock] Failed to load {}", configPath, e);
            }
        }
        instance = new CustomBlockConfig();
        instance.blocks = new ArrayList<>();
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
            SRE.LOGGER.error("[CustomBlock] Failed to save {}", configPath, e);
        }
    }

    public void saveToDefaultPath() {
        saveToPath(getConfigPath());
    }

    public static CustomBlockConfig loadFromDefaultPath() {
        return loadFromPath(getConfigPath());
    }

    /** 一律从存档文件读取配置（与自定义物品保持一致）。 */
    public static CustomBlockConfig loadPreferWorldPath(MinecraftServer server) {
        if (server != null) {
            try {
                Path worldPath = server.getWorldPath(LevelResource.ROOT);
                CustomBlockConfig cfg = loadFromFile(worldPath);
                if (cfg != null && cfg.blocks != null && !cfg.blocks.isEmpty())
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
                        CustomBlockConfig cfg = loadFromFile(worldDir);
                        if (cfg != null && cfg.blocks != null && !cfg.blocks.isEmpty())
                            return cfg;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return getInstance();
    }

    public void addBlock(CustomBlockData data) {
        if (data == null) {
            return;
        }
        data.sanitize();
        blocks.add(data);
    }

    public void removeBlock(String id) {
        if (id == null) {
            return;
        }
        blocks.removeIf(block -> block != null && id.equals(block.id));
    }

    public CustomBlockData findBlock(String id) {
        if (id == null) {
            return null;
        }
        return blocks.stream().filter(block -> block != null && id.equals(block.id)).findFirst().orElse(null);
    }

    /** 该 id 是否已被别的条目占用（{@code exceptId} 是当前正在编辑的条目自身 id）。 */
    public boolean isIdTaken(String id, String exceptId) {
        if (id == null || id.isBlank()) {
            return false;
        }
        for (CustomBlockData block : blocks) {
            if (block == null || block.id == null) {
                continue;
            }
            if (block.id.equals(id) && !block.id.equals(exceptId)) {
                return true;
            }
        }
        return false;
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

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

package io.wifi.starrailexpress.customitem;

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
 * 自定义列车物品配置文件包装类（独立文件：{@code sre_custom_items.json}）。
 *
 * <p>
 * 结构与 {@link io.wifi.starrailexpress.customrole.CustomRoleConfig} /
 * {@link io.wifi.starrailexpress.custommodifier.CustomModifierConfig} 完全对齐：
 * 权威数据在服务端世界存档，客户端 config 目录只是同步副本。
 */
public class CustomItemConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String FILE_NAME = "sre_custom_items.json";

    @SerializedName("items")
    public List<CustomItemData> items = new ArrayList<>();

    private static CustomItemConfig instance;

    public static CustomItemConfig getInstance() {
        if (instance == null) {
            instance = new CustomItemConfig();
            instance.items = new ArrayList<>();
        }
        return instance;
    }

    public static Path getSaveDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static Path getConfigPath() {
        return getSaveDir().resolve(FILE_NAME);
    }

    public static CustomItemConfig loadFromFile(Path worldPath) {
        return loadFromPath(worldPath.resolve(FILE_NAME));
    }

    private static CustomItemConfig loadFromPath(Path configPath) {
        if (Files.exists(configPath)) {
            try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                CustomItemConfig loaded = GSON.fromJson(reader, CustomItemConfig.class);
                if (loaded != null && loaded.items != null) {
                    instance = loaded;
                    for (CustomItemData data : instance.items) {
                        if (data != null) {
                            data.sanitize();
                        }
                    }
                    return instance;
                }
            } catch (Exception e) {
                SRE.LOGGER.error("[CustomItem] Failed to load {}", configPath, e);
            }
        }
        instance = new CustomItemConfig();
        instance.items = new ArrayList<>();
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
            SRE.LOGGER.error("[CustomItem] Failed to save {}", configPath, e);
        }
    }

    public void saveToDefaultPath() {
        saveToPath(getConfigPath());
    }

    public static CustomItemConfig loadFromDefaultPath() {
        return loadFromPath(getConfigPath());
    }

    /** 一律从存档文件读取配置（与自定义职业保持一致）。 */
    public static CustomItemConfig loadPreferWorldPath(MinecraftServer server) {
        if (server != null) {
            try {
                Path worldPath = server.getWorldPath(LevelResource.ROOT);
                CustomItemConfig cfg = loadFromFile(worldPath);
                if (cfg != null && cfg.items != null && !cfg.items.isEmpty())
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
                        CustomItemConfig cfg = loadFromFile(worldDir);
                        if (cfg != null && cfg.items != null && !cfg.items.isEmpty())
                            return cfg;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return getInstance();
    }

    public void addItem(CustomItemData data) {
        if (data == null) {
            return;
        }
        data.sanitize();
        items.add(data);
    }

    public void removeItem(String id) {
        if (id == null) {
            return;
        }
        items.removeIf(item -> item != null && id.equals(item.id));
    }

    public CustomItemData findItem(String id) {
        if (id == null) {
            return null;
        }
        return items.stream().filter(item -> item != null && id.equals(item.id)).findFirst().orElse(null);
    }

    /**
     * 该 id 是否已被别的条目占用。
     *
     * @param id       待检查的 id
     * @param exceptId 正在编辑的条目原始 id（会跳过它，避免「自己和自己冲突」）
     * @param self     正在编辑的条目对象本身（按引用跳过，避免直接编辑列表内对象时误判）
     */
    public boolean isIdTaken(String id, String exceptId, CustomItemData self) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String wanted = id.trim();
        for (CustomItemData item : items) {
            if (item == null || item == self || item.id == null) {
                continue;
            }
            String existing = item.id.trim();
            if (existing.equalsIgnoreCase(wanted) && !existing.equalsIgnoreCase(exceptId == null ? "" : exceptId.trim())) {
                return true;
            }
        }
        return false;
    }

    /** 该 id 是否已被别的条目占用（不含自身引用）。 */
    public boolean isIdTaken(String id, String exceptId) {
        return isIdTaken(id, exceptId, null);
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

package net.exmo.sre.sixtyseconds.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.Noellesroles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 全局随机物资箱类别配置：按等级（low/high）存储已启用的 loot 类别集合。
 * <b>所有随机箱共享同一份配置</b>，不再每箱各存 NBT——管理员在任意一个随机箱上编辑保存后，
 * 同等级的全部随机箱立刻生效。每个世界有一份独立的 JSON 文件（世界存档目录
 * {@code sixty_seconds_random_box_config.json}），重启不丢失。
 * <p>
 * 仿 {@link SixtySecondsLootStore}：首次运行写入默认类别；GUI 编辑后经 C2S 调 {@link #save} 落盘。
 */
public final class SixtySecondsRandomBoxConfigStore {
    public static final String FILE_NAME = "sixty_seconds_random_box_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, Data> CACHE = new WeakHashMap<>();

    private SixtySecondsRandomBoxConfigStore() {
    }

    /** 读取本服务实例缓存的全局配置；首次访问从文件加载或写入默认配置。 */
    public static Data get(ServerLevel level) {
        return CACHE.computeIfAbsent(level.getServer(), ignored -> loadOrDefault(level));
    }

    /** 保存并落盘（GUI 编辑后调用）。 */
    public static void save(ServerLevel level, Data data) {
        CACHE.put(level.getServer(), data);
        writeFile(level, data);
    }

    /** 清空缓存（供 reload 命令使用）。 */
    public static void invalidate(ServerLevel level) {
        CACHE.remove(level.getServer());
    }

    // ── 文件 I/O ────────────────────────────────────────────────────

    private static Path path(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    private static Data loadOrDefault(ServerLevel level) {
        Path path = path(level);
        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Data data = GSON.fromJson(reader, Data.class);
                if (data != null) {
                    return data;
                }
            } catch (IOException | RuntimeException e) {
                Noellesroles.LOGGER.warn("[60s] 读取 {} 失败：{}", FILE_NAME, e.toString());
            }
        }
        Data data = defaultConfig();
        writeFile(level, data);
        return data;
    }

    private static void writeFile(ServerLevel level, Data data) {
        Path path = path(level);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            Noellesroles.LOGGER.warn("[60s] 写入 {} 失败：{}", FILE_NAME, e.toString());
        }
    }

    // ── 默认配置 ─────────────────────────────────────────────────────

    /** 默认配置：low 和 high 等级的全部默认类别启用。 */
    public static Data defaultConfig() {
        Data data = new Data();
        data.tierEnabled.put("low", new ArrayList<>(List.of(
                "food", "water", "medicine", "tool", "material", "field")));
        data.tierEnabled.put("high", new ArrayList<>(List.of(
                "advanced_food", "advanced_material", "advanced_medicine",
                "advanced_tool", "advanced_weapon", "advanced_rare")));
        return data;
    }

    // ── 数据模型 ─────────────────────────────────────────────────────

    /**
     * 全局随机箱配置数据。使用 {@code List<String>} 而非 {@code Set<String>} 以确保
     * Gson 反序列化兼容性。
     */
    public static class Data {
        /** tier（如 "low"/"high"）→ 已启用的类别列表（保持插入顺序）。 */
        public Map<String, List<String>> tierEnabled = new LinkedHashMap<>();

        /** 获取某等级的已启用类别集合；从未配置过则返回该等级的默认集合。 */
        public Set<String> getEnabled(String tier) {
            List<String> list = tierEnabled.get(tier);
            if (list != null && !list.isEmpty()) {
                return new LinkedHashSet<>(list);
            }
            return defaultForTier(tier);
        }

        /** 设置某等级的已启用类别集合。 */
        public void setEnabled(String tier, Set<String> categories) {
            tierEnabled.put(tier, new ArrayList<>(categories));
        }

        private static Set<String> defaultForTier(String tier) {
            if ("high".equals(tier)) {
                return new LinkedHashSet<>(List.of(
                        "advanced_food", "advanced_material", "advanced_medicine",
                        "advanced_tool", "advanced_weapon", "advanced_rare"));
            }
            return new LinkedHashSet<>(List.of(
                    "food", "water", "medicine", "tool", "material", "field"));
        }
    }
}

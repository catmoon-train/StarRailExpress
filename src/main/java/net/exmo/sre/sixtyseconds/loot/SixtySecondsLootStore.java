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
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 共享 loot 表的本地文件读写 + 每服缓存（世界存档目录 {@code sixty_seconds_loot.json}）。
 * 仿 {@code ServerMapConfig}：首次运行写入默认表；GUI 编辑后经 C2S 调 {@link #save} 落盘（不只靠 sync）。
 */
public final class SixtySecondsLootStore {
    public static final String FILE_NAME = "sixty_seconds_loot.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, SixtySecondsLootTable> CACHE = new WeakHashMap<>();

    private SixtySecondsLootStore() {
    }

    private static Path path(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    public static SixtySecondsLootTable get(ServerLevel level) {
        return CACHE.computeIfAbsent(level.getServer(), ignored -> loadOrDefault(level));
    }

    public static void save(ServerLevel level, SixtySecondsLootTable table) {
        CACHE.put(level.getServer(), table);
        writeFile(level, table);
    }

    private static SixtySecondsLootTable loadOrDefault(ServerLevel level) {
        Path path = path(level);
        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                SixtySecondsLootTable table = GSON.fromJson(reader, SixtySecondsLootTable.class);
                if (table != null && table.categories != null) {
                    return table;
                }
            } catch (IOException | RuntimeException e) {
                Noellesroles.LOGGER.warn("[60s] 读取 {} 失败：{}", FILE_NAME, e.toString());
            }
        }
        SixtySecondsLootTable table = SixtySecondsLootTable.defaultTable();
        writeFile(level, table);
        return table;
    }

    private static void writeFile(ServerLevel level, SixtySecondsLootTable table) {
        Path path = path(level);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(table, writer);
            }
        } catch (IOException e) {
            Noellesroles.LOGGER.warn("[60s] 写入 {} 失败：{}", FILE_NAME, e.toString());
        }
    }
}

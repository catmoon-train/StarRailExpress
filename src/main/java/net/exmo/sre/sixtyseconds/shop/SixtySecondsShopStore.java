package net.exmo.sre.sixtyseconds.shop;

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
 * NPC 商店表的本地文件读写 + 每服缓存（世界存档目录 {@code sixty_seconds_npc_shop.json}）。
 * 仿 {@link net.exmo.sre.sixtyseconds.loot.SixtySecondsLootStore}：首次运行写入默认表；
 * GUI 编辑后经 C2S 调 {@link #save} 落盘（<b>不只靠 sync</b>——CCA sync 只同步不落盘）。
 * <p>
 * 存服务器存档根目录而<b>不按图</b>：商人库存是经济内容，与地图几何无关，换图应沿用。
 * NPC 的<b>放置坐标</b>才是地图数据，走 {@code SixtySecondsConfig.npcSpawns} 的按图 JSON。
 */
public final class SixtySecondsShopStore {
    public static final String FILE_NAME = "sixty_seconds_npc_shop.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, SixtySecondsShopTable> CACHE = new WeakHashMap<>();

    private SixtySecondsShopStore() {
    }

    private static Path path(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    public static SixtySecondsShopTable get(ServerLevel level) {
        return CACHE.computeIfAbsent(level.getServer(), ignored -> loadOrDefault(level));
    }

    public static void save(ServerLevel level, SixtySecondsShopTable table) {
        CACHE.put(level.getServer(), table);
        writeFile(level, table);
    }

    private static SixtySecondsShopTable loadOrDefault(ServerLevel level) {
        Path path = path(level);
        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                SixtySecondsShopTable table = GSON.fromJson(reader, SixtySecondsShopTable.class);
                if (table != null && table.profiles != null) {
                    return table;
                }
            } catch (IOException | RuntimeException e) {
                Noellesroles.LOGGER.warn("[60s] 读取 {} 失败：{}", FILE_NAME, e.toString());
            }
        }
        SixtySecondsShopTable table = SixtySecondsShopTable.defaultTable();
        writeFile(level, table);
        return table;
    }

    private static void writeFile(ServerLevel level, SixtySecondsShopTable table) {
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

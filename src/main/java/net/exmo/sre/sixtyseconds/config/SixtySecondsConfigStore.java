package net.exmo.sre.sixtyseconds.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.Noellesroles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 末日60秒模式配置的本地文件读写，<b>按当前列车地图分文件</b>：
 * {@code 世界存档/sixty_seconds_maps/<地图名>.json}（地图名取 {@code AreasWorldComponent.mapName}，
 * {@code GameUtils.startGame} 每次开局都会重载当前地图，故开局读到的一定是该地图自己的 60s 配置）。
 * 每张 60s 地图各存各的区域配置，切图/换图不会互相覆盖。
 * <p>
 * 兼容旧档：未加载地图（mapName 为空）或按图文件不存在时，回退读写旧的全局
 * {@code sixty_seconds_config.json}；在按图路径下保存过一次后即完成迁移。
 * 仿 {@code io.wifi.starrailexpress.game.data.ServerMapConfig} 的 Gson 单例 + 落盘做法。
 * <p>
 * ⚠️ CCA/组件 {@code sync()} 只同步不落盘；GUI/命令改动必须走此处显式写文件才能重启不丢
 * （见 memory {@code configsync-gui-edit-not-persisted-server}）。
 */
public final class SixtySecondsConfigStore {
    /** 旧版全局配置文件名（兼容回退用）。 */
    public static final String LEGACY_FILE_NAME = "sixty_seconds_config.json";
    /** 按地图分文件的存放目录（世界存档目录下）。 */
    public static final String MAPS_DIR = "sixty_seconds_maps";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SixtySecondsConfigStore() {
    }

    private static Path legacyPath(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve(LEGACY_FILE_NAME);
    }

    /** 当前地图对应的配置文件；未加载地图时返回 empty（回退旧全局文件）。 */
    private static Optional<Path> mapPath(ServerLevel level) {
        String mapName = AreasWorldComponent.KEY.get(level).mapName;
        if (mapName == null || mapName.isBlank()) {
            return Optional.empty();
        }
        // 地图名可能带子目录分隔符等，统一压成安全文件名
        String safe = mapName.trim().replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff._-]", "_");
        return Optional.of(level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve(MAPS_DIR).resolve(safe + ".json"));
    }

    /** 供命令/日志显示：当前实际读写的配置文件（世界存档目录内的相对路径）。 */
    public static String describe(ServerLevel level) {
        return mapPath(level)
                .map(path -> MAPS_DIR + "/" + path.getFileName())
                .orElse(LEGACY_FILE_NAME);
    }

    /** 读取配置：优先当前地图的按图文件，缺失时回退旧全局文件；都没有或损坏返回 empty。 */
    public static Optional<SixtySecondsConfig> load(ServerLevel level) {
        Optional<Path> perMap = mapPath(level);
        if (perMap.isPresent() && Files.exists(perMap.get())) {
            return read(perMap.get());
        }
        Path legacy = legacyPath(level);
        if (Files.exists(legacy)) {
            return read(legacy);
        }
        return Optional.empty();
    }

    private static Optional<SixtySecondsConfig> read(Path configPath) {
        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            SixtySecondsConfig config = GSON.fromJson(reader, SixtySecondsConfig.class);
            return Optional.ofNullable(config);
        } catch (IOException | RuntimeException e) {
            Noellesroles.LOGGER.warn("[60s] 读取 {} 失败：{}", configPath.getFileName(), e.toString());
            return Optional.empty();
        }
    }

    /** 便捷别名：开局取当前配置。 */
    public static Optional<SixtySecondsConfig> current(ServerLevel level) {
        return load(level);
    }

    /** 保存到当前地图的按图文件（未加载地图时写旧全局文件）。 */
    public static void save(ServerLevel level, SixtySecondsConfig config) {
        Path configPath = mapPath(level).orElseGet(() -> legacyPath(level));
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            Noellesroles.LOGGER.info("[60s] 配置已保存：{}", describe(level));
        } catch (IOException e) {
            Noellesroles.LOGGER.warn("[60s] 写入 {} 失败：{}", configPath.getFileName(), e.toString());
        }
    }
}

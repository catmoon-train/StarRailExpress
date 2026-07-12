package net.exmo.sre.sixtyseconds.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
 * 末日60秒模式配置的本地文件读写（世界存档目录 {@code sixty_seconds_config.json}）。
 * 仿 {@code io.wifi.starrailexpress.game.data.ServerMapConfig} 的 Gson 单例 + 落盘做法。
 * <p>
 * ⚠️ CCA/组件 {@code sync()} 只同步不落盘；GUI/命令改动必须走此处显式写文件才能重启不丢
 * （见 memory {@code configsync-gui-edit-not-persisted-server}）。
 */
public final class SixtySecondsConfigStore {
    public static final String FILE_NAME = "sixty_seconds_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SixtySecondsConfigStore() {
    }

    private static Path path(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    /** 读取配置；文件不存在或损坏返回 empty（此时开局不克隆，仅日志告警）。 */
    public static Optional<SixtySecondsConfig> load(ServerLevel level) {
        Path configPath = path(level);
        if (!Files.exists(configPath)) {
            return Optional.empty();
        }
        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            SixtySecondsConfig config = GSON.fromJson(reader, SixtySecondsConfig.class);
            return Optional.ofNullable(config);
        } catch (IOException | RuntimeException e) {
            Noellesroles.LOGGER.warn("[60s] 读取 {} 失败：{}", FILE_NAME, e.toString());
            return Optional.empty();
        }
    }

    /** 便捷别名：开局取当前配置。 */
    public static Optional<SixtySecondsConfig> current(ServerLevel level) {
        return load(level);
    }

    public static void save(ServerLevel level, SixtySecondsConfig config) {
        Path configPath = path(level);
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            Noellesroles.LOGGER.warn("[60s] 写入 {} 失败：{}", FILE_NAME, e.toString());
        }
    }
}

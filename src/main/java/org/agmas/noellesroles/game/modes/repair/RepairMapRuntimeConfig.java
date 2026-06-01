package org.agmas.noellesroles.game.modes.repair;

import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.game.data.ServerMapConfig;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

public final class RepairMapRuntimeConfig {
    private RepairMapRuntimeConfig() {
    }

    public static Optional<MapConfig.MapEntry> currentMap(ServerLevel level) {
        // 首先从投票缓存中查找（如果有正在进行的投票）
        for (MapConfig.MapEntry entry : ServerMapConfig.cache_maps) {
            if (entry != null && entry.repair != null) {
                return Optional.of(entry);
            }
        }
        // 从服务器配置中查找所有带 repair 配置的地图
        ServerMapConfig config = ServerMapConfig.getInstance(level);
        if (config.getMaps() != null) {
            Optional<MapConfig.MapEntry> found = config.getMaps().stream()
                    .filter(entry -> entry != null && entry.repair != null)
                    .findFirst();
            if (found.isPresent()) {
                return found;
            }
        }
        // 最后从内置配置中查找
        MapConfig builtin = MapConfig.getInstance();
        if (builtin != null && builtin.getMaps() != null) {
            return builtin.getMaps().stream()
                    .filter(entry -> entry != null && entry.repair != null)
                    .findFirst();
        }
        return Optional.empty();
    }

    public static Optional<MapConfig.RepairConfig> current(ServerLevel level) {
        return currentMap(level).map(entry -> entry.repair);
    }
}

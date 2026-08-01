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

package net.exmo.sre.repair.state;

import net.exmo.sre.repair.*;
import net.exmo.sre.repair.role.*;
import net.exmo.sre.repair.arena.*;
import net.exmo.sre.repair.event.*;
import net.exmo.sre.repair.util.*;

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

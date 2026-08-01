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

package io.wifi.starrailexpress.client.stats;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.data.PlayerStatsData;
import io.wifi.starrailexpress.stats.PlayerStats;
import io.wifi.starrailexpress.util.PlayerStatsSerializer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientPlayerStatsCache {
    private static final Map<UUID, PlayerStats> STATS = new ConcurrentHashMap<>();

    private ClientPlayerStatsCache() {
    }

    public static PlayerStats getOrEmpty(UUID playerUuid) {
        return STATS.computeIfAbsent(playerUuid, PlayerStats::new);
    }

    public static void update(UUID playerUuid, String json) {
        try {
            PlayerStatsData data = PlayerStatsSerializer.fromJson(json);
            getOrEmpty(playerUuid).replaceWith(data);
        } catch (RuntimeException exception) {
            SRE.LOGGER.error("Failed to apply synced stats for {}", playerUuid, exception);
        }
    }

    public static void clear() {
        STATS.clear();
    }
}

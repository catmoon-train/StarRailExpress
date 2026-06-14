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

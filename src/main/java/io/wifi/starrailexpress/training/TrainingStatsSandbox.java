package io.wifi.starrailexpress.training;

import io.wifi.starrailexpress.stats.PlayerStats;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 无持久化监听器的训练统计；与正式缓存完全分离，随服务器停止丢弃。 */
public final class TrainingStatsSandbox {
    private final Map<UUID, PlayerStats> entries = new ConcurrentHashMap<>();

    public PlayerStats get(UUID player) {
        return entries.computeIfAbsent(player, PlayerStats::new);
    }

    public void clear() { entries.clear(); }
}

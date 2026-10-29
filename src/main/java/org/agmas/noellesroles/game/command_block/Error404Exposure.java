package org.agmas.noellesroles.game.command_block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 区域内连续暴露计时：离开即归零，每名新进入者都拥有完整的逃离窗口。 */
public final class Error404Exposure {
    private final Map<UUID, Long> entered = new HashMap<>();

    public boolean sample(UUID player, boolean inside, long now) {
        if (!inside) {
            entered.remove(player);
            return false;
        }
        long start = entered.computeIfAbsent(player, id -> now);
        return now - start >= 20;
    }

    public boolean contains(UUID player) { return entered.containsKey(player); }
    public void forget(UUID player) { entered.remove(player); }
    public static int secondsLeft(long end, long now) { return (int) Math.max(0, (end - now + 19) / 20); }
}

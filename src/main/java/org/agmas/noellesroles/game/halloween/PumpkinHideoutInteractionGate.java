package org.agmas.noellesroles.game.halloween;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 同一次右键可能产生多个主手实体交互包；进入/退出共用防重窗口。仅服务端主线程使用。 */
final class PumpkinHideoutInteractionGate {
    private static final int COOLDOWN_TICKS = 5;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    boolean tryUse(UUID player, long now) {
        Long previous = lastUse.get(player);
        if (previous != null && now - previous < COOLDOWN_TICKS) return false;
        lastUse.put(player, now);
        return true;
    }

    void remove(UUID player) { lastUse.remove(player); }
}

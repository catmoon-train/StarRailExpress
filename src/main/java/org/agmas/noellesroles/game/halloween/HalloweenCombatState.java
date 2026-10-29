package org.agmas.noellesroles.game.halloween;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 不依赖 Minecraft 的单局事务：暂停时钟、尸体占用、任务进度与一次性魂契。 */
final class HalloweenCombatState {
    private long ticks;
    private final Set<UUID> claimedBodies = new HashSet<>();
    private final Set<UUID> acceptedPacts = new HashSet<>();
    private final Map<UUID, Integer> tasks = new HashMap<>();

    long now() { return ticks; }

    void tick(boolean paused) {
        if (!paused) ticks++;
    }

    boolean claimBody(UUID body) { return body != null && claimedBodies.add(body); }

    boolean acceptPact(UUID player) { return acceptedPacts.add(player); }

    boolean hasAcceptedPact(UUID player) { return acceptedPacts.contains(player); }

    boolean completeTask(UUID player, int required) {
        int count = tasks.getOrDefault(player, 0) + 1;
        if (count >= Math.max(1, required)) {
            tasks.put(player, 0);
            return true;
        }
        tasks.put(player, count);
        return false;
    }

    void disconnect(UUID player) {
        tasks.remove(player);
        // 已交付资格不能因重连再次购买；待复活事件由战斗模块单独取消。
    }
}

package org.agmas.noellesroles.game.scanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** 单局扫描账本：按死者UUID去重，绝不按职业或扫描仪位置去重。 */
public final class IdentityScannerLedger {
    public record Identity(UUID player, String name, String roleId, int color) { }
    private final UUID roundId = UUID.randomUUID();
    private final Map<UUID, Identity> deaths = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> seen = new HashMap<>();
    private final Map<UUID, Long> portableReadyAt = new HashMap<>();

    public UUID roundId() { return roundId; }
    public void recordDeath(Identity identity) { deaths.put(identity.player(), identity); }
    public Identity death(UUID player) { return deaths.get(player); }
    public List<Identity> candidates(UUID viewer, Predicate<UUID> currentlyDead) {
        Set<UUID> scanned = seen.getOrDefault(viewer, Set.of());
        List<Identity> result = new ArrayList<>();
        for (Identity identity : deaths.values()) {
            if (!scanned.contains(identity.player()) && currentlyDead.test(identity.player())) result.add(identity);
        }
        return result;
    }
    public void markScanned(UUID viewer, UUID deadPlayer) {
        seen.computeIfAbsent(viewer, key -> new HashSet<>()).add(deadPlayer);
    }
    public int portableRemaining(UUID viewer, long now) {
        return remaining(portableReadyAt.getOrDefault(viewer, 0L), now);
    }
    public void portableScanned(UUID viewer, long now) { portableReadyAt.put(viewer, now + 120 * 20); }
    public static int remaining(long deadline, long now) {
        return deadline <= now ? 0 : (int) Math.min(Integer.MAX_VALUE, deadline - now);
    }
}

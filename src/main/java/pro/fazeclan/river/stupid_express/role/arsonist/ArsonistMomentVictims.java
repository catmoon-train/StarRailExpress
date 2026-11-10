package pro.fazeclan.river.stupid_express.role.arsonist;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** 单次纵火时刻内的死者名单；取出即结束，避免死亡回调重入时重复复活。 */
final class ArsonistMomentVictims {
    private UUID owner;
    private final Set<UUID> victims = new LinkedHashSet<>();

    void begin(UUID owner) {
        this.owner = owner;
        victims.clear();
    }

    boolean record(UUID victim) {
        return owner != null && victim != null && !owner.equals(victim) && victims.add(victim);
    }

    boolean contains(UUID victim) { return victims.contains(victim); }

    Set<UUID> snapshot() { return Set.copyOf(victims); }

    Set<UUID> finish() {
        Set<UUID> result = snapshot();
        owner = null;
        victims.clear();
        return result;
    }
}

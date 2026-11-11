package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 判死登记簿：只有命中归零才登记，清除与结束均消费记录，避免重复结算。 */
public final class SackGhostVerdicts {
    private final Set<UUID> marked = new LinkedHashSet<>();

    public boolean markAfterHit(UUID target, double sanFraction) {
        return target != null && SackGhostRules.isCondemned(sanFraction) && marked.add(target);
    }

    public boolean contains(UUID target) { return marked.contains(target); }
    public int size() { return marked.size(); }
    public boolean cleanse(UUID target) { return marked.remove(target); }
    public List<UUID> snapshot() { return List.copyOf(marked); }
    public void clear() { marked.clear(); }

    public List<UUID> finish(boolean natural) {
        List<UUID> result = natural ? snapshot() : List.of();
        clear();
        return result;
    }

    public void restore(Collection<UUID> targets) {
        clear();
        targets.stream().filter(java.util.Objects::nonNull).limit(128).forEach(marked::add);
    }
}

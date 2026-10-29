package org.agmas.noellesroles.game.halloween.pumpkin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 每批糖果有服务端数量预算；拆栈、转手、重新投出均不能扩大预算。 */
public final class CandyLedger {
    private record Batch(UUID owner, int remaining) {}
    private final Map<UUID, Batch> batches = new HashMap<>();

    public UUID issue(UUID owner, int count) {
        UUID id = UUID.randomUUID();
        batches.put(id, new Batch(owner, Math.max(0, count)));
        return id;
    }

    /** 自己吃同样消耗这枚糖的归因资格，但不奖励。 */
    public boolean consume(UUID batch, UUID owner, UUID eater) {
        Batch b = batches.get(batch);
        if (b == null || !b.owner.equals(owner) || b.remaining <= 0) return false;
        batches.put(batch, new Batch(owner, b.remaining - 1));
        return !owner.equals(eater);
    }

    public void forget(UUID owner) { batches.values().removeIf(b -> b.owner.equals(owner)); }
}

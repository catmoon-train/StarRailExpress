package io.wifi.starrailexpress.training;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** 固定八席的纯逻辑租约池；所有分配、归还与关闭操作具有同一把锁。 */
public final class TrainingCampPool implements AutoCloseable {
    public static final int CAPACITY = 8;
    // 跨池实例也不复用 token，避免上一服务端生命周期的延迟归还命中新池。
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private final Lease[] slots = new Lease[CAPACITY];
    private boolean closed;
    private int size;

    public synchronized Lease acquire(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        if (closed) return null;
        for (Lease lease : slots) {
            if (lease != null && lease.owner().equals(owner)) return lease;
        }
        for (int slot = 0; slot < CAPACITY; slot++) {
            if (slots[slot] == null) {
                long generation = GENERATIONS.updateAndGet(value -> Math.incrementExact(value));
                Lease lease = new Lease(owner, slot, generation);
                slots[slot] = lease;
                size++;
                return lease;
            }
        }
        return null;
    }

    public synchronized boolean release(Lease lease) {
        if (lease == null || lease.slot() < 0 || lease.slot() >= CAPACITY
                || !lease.equals(slots[lease.slot()])) return false;
        slots[lease.slot()] = null;
        size--;
        return true;
    }

    public synchronized boolean owns(UUID owner, int slot) {
        return slot >= 0 && slot < CAPACITY && slots[slot] != null
                && slots[slot].owner().equals(owner);
    }

    public synchronized int size() { return size; }

    @Override
    public synchronized void close() {
        closed = true;
        java.util.Arrays.fill(slots, null);
        size = 0;
    }

    public record Lease(UUID owner, int slot, long generation) { }
}

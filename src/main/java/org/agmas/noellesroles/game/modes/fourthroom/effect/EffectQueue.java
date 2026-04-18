package org.agmas.noellesroles.game.modes.fourthroom.effect;

import net.minecraft.core.BlockPos;

import java.util.PriorityQueue;

/**
 * Client-side priority queue that fires EffectEvents at their scheduled times.
 * Each event's absolute fire time = baseTime + event.timeOffset().
 */
public final class EffectQueue {

    private record ScheduledEvent(long fireTimeMs, EffectEvent event) implements Comparable<ScheduledEvent> {
        @Override
        public int compareTo(ScheduledEvent other) {
            return Long.compare(this.fireTimeMs, other.fireTimeMs);
        }
    }

    private final PriorityQueue<ScheduledEvent> queue = new PriorityQueue<>();
    private BlockPos origin = BlockPos.ZERO;

    public void setOrigin(BlockPos origin) {
        this.origin = origin;
    }

    /**
     * Enqueue a batch of effects with a shared base time (now).
     */
    public void enqueue(Iterable<EffectEvent> effects) {
        long baseTime = System.currentTimeMillis();
        for (EffectEvent event : effects) {
            queue.add(new ScheduledEvent(baseTime + event.timeOffset(), event));
        }
    }

    /**
     * Enqueue a single effect to fire after the given delay.
     */
    public void enqueue(EffectEvent event) {
        long baseTime = System.currentTimeMillis();
        queue.add(new ScheduledEvent(baseTime + event.timeOffset(), event));
    }

    /**
     * Called every client tick. Fires all events whose time has arrived.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        while (!queue.isEmpty() && queue.peek().fireTimeMs <= now) {
            ScheduledEvent scheduled = queue.poll();
            if (scheduled != null) {
                scheduled.event.executeClient(origin);
            }
        }
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
    }
}

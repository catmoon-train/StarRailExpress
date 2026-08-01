/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

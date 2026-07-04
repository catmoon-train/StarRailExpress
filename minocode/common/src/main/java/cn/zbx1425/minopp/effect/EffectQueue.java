package cn.zbx1425.minopp.effect;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayPriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;

public class EffectQueue {

    private final PriorityQueue<TimedEvent> queue = new ObjectArrayPriorityQueue<>(Comparator.comparingLong(a -> a.time));

    public void tick(Level level) {
        long time = System.currentTimeMillis();
        synchronized (queue) {
            while (!queue.isEmpty() && queue.first().time <= time) {
                queue.dequeue().summon(level);
            }
        }
    }

    public void addAll(List<EffectEvent> events, BlockPos origin, Player self, boolean selfIsPartOfSourceGame) {
        synchronized (queue) {
            long time = System.currentTimeMillis();
            for (EffectEvent event : events) {
                if (event.target().isEmpty() || event.target().get().equals(self.getGameProfile().getId())) {
                    queue.enqueue(new TimedEvent(event, time, origin, selfIsPartOfSourceGame));
                }
            }
        }
    }

    private static class TimedEvent {

        private final EffectEvent event;
        private final long time;
        private final BlockPos origin;
        private final boolean selfIsPartOfSourceGame;

        public TimedEvent(EffectEvent incoming, long baseTime, BlockPos origin, boolean selfIsPartOfSourceGame) {
            this.event = incoming;
            this.time = baseTime + incoming.timeOffset();
            this.origin = origin;
            this.selfIsPartOfSourceGame = selfIsPartOfSourceGame;
        }

        public void summon(Level level) {
            event.summonClient(level, origin, selfIsPartOfSourceGame);
        }
    }
}

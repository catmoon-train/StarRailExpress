package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task;

import java.util.Comparator;
import java.util.List;

/** Pure task-stickiness policy shared by the Minecraft task adapter and tests. */
public final class FakeSteveTaskSelection {
    public static final long STALL_TICKS = 45L * 20L;

    private FakeSteveTaskSelection() {
    }

    public static Task choose(List<Candidate> candidates, Task current,
            long currentStartedTick, long now) {
        if (current != null && now - currentStartedTick <= STALL_TICKS
                && candidates.stream().anyMatch(candidate -> candidate.task() == current && !candidate.backedOff())) {
            return current;
        }
        return candidates.stream()
                .filter(candidate -> !candidate.backedOff())
                .filter(candidate -> candidate.task() != current || candidates.size() == 1)
                .min(Comparator.comparingDouble(Candidate::estimatedDistance))
                .map(Candidate::task)
                .orElse(null);
    }

    public record Candidate(Task task, double estimatedDistance, boolean backedOff) {
    }
}

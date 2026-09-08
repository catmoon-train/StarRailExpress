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

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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FakeSteveTaskSelectionTest {
    @Test
    void keepsAReachableTaskWhileItIsMakingTimelyProgress() {
        Task selected = FakeSteveTaskSelection.choose(
                List.of(new FakeSteveTaskSelection.Candidate(Task.SLEEP, 20.0D, false),
                        new FakeSteveTaskSelection.Candidate(Task.CHAIR, 5.0D, false)),
                Task.SLEEP, 100L, 500L);

        assertEquals(Task.SLEEP, selected);
    }

    @Test
    void switchesToAnotherTaskAfterTheCurrentOneStalls() {
        Task selected = FakeSteveTaskSelection.choose(
                List.of(new FakeSteveTaskSelection.Candidate(Task.SLEEP, 20.0D, false),
                        new FakeSteveTaskSelection.Candidate(Task.CHAIR, 5.0D, false)),
                Task.SLEEP, 100L, 1_101L);

        assertEquals(Task.CHAIR, selected);
    }
}

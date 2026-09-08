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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FakeSteveTaskPlannerTest {

    @Test
    void everyWorldTaskHasAHumanLikeExecutionStrategy() {
        Map<Task, FakeSteveTaskPlanner.Strategy> expected = Map.ofEntries(
                Map.entry(Task.SLEEP, FakeSteveTaskPlanner.Strategy.BLOCK_INTERACT),
                Map.entry(Task.RAED_BOOK, FakeSteveTaskPlanner.Strategy.BLOCK_INTERACT),
                Map.entry(Task.EAT, FakeSteveTaskPlanner.Strategy.CONSUME),
                Map.entry(Task.DRINK, FakeSteveTaskPlanner.Strategy.CONSUME),
                Map.entry(Task.EXERCISE, FakeSteveTaskPlanner.Strategy.HOLD_POSITION),
                Map.entry(Task.MEDITATE, FakeSteveTaskPlanner.Strategy.CROUCH),
                Map.entry(Task.BATHE, FakeSteveTaskPlanner.Strategy.HOLD_POSITION),
                Map.entry(Task.CHAIR, FakeSteveTaskPlanner.Strategy.BLOCK_INTERACT),
                Map.entry(Task.NOTE_BLOCK, FakeSteveTaskPlanner.Strategy.BLOCK_INTERACT),
                Map.entry(Task.TOILET, FakeSteveTaskPlanner.Strategy.BLOCK_INTERACT),
                Map.entry(Task.BE_ALONE, FakeSteveTaskPlanner.Strategy.HOLD_POSITION),
                Map.entry(Task.BREATHE, FakeSteveTaskPlanner.Strategy.HOLD_POSITION),
                Map.entry(Task.LIGHT_STOVE, FakeSteveTaskPlanner.Strategy.BLOCK_INTERACT),
                Map.entry(Task.CLEAN_DUST, FakeSteveTaskPlanner.Strategy.BLOCK_INTERACT),
                Map.entry(Task.TRANSPORT, FakeSteveTaskPlanner.Strategy.BLOCK_INTERACT),
                Map.entry(Task.PRAY, FakeSteveTaskPlanner.Strategy.HOLD_POSITION),
                Map.entry(Task.PRUNE_BUSH, FakeSteveTaskPlanner.Strategy.BLOCK_INTERACT),
                Map.entry(Task.HARVEST_CROP, FakeSteveTaskPlanner.Strategy.JUMP));

        expected.forEach((task, strategy) ->
                assertEquals(strategy, FakeSteveTaskPlanner.strategy(task).orElseThrow(), task.name()));
        assertFalse(FakeSteveTaskPlanner.strategy(Task.MANIC).isPresent());
        assertFalse(FakeSteveTaskPlanner.strategy(Task.CUSTOM).isPresent());
    }
}

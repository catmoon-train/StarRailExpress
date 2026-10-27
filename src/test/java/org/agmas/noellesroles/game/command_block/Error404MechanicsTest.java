package org.agmas.noellesroles.game.command_block;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.agmas.noellesroles.game.command_block.Error404Pathfinder.Cell;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Error404MechanicsTest {
    private static Error404Pathfinder.Grid flat(Set<Cell> walls) {
        return new Error404Pathfinder.Grid() {
            public boolean clear(Cell cell) { return cell.y() >= 0; }
            public boolean standable(Cell cell) { return cell.y() == 0; }
        };
    }

    @Test void findsWalkableDetourRatherThanPassingThroughWalls() {
        Set<Cell> walls = Set.of(new Cell(1, 0, 0), new Cell(1, 0, 1));
        List<Cell> path = Error404Pathfinder.find(flat(walls), new Cell(0, 0, 0), new Cell(3, 0, 0));
        assertFalse(path.isEmpty());
        assertEquals(new Cell(3, 0, 0), path.getLast());
        assertTrue(path.contains(new Cell(1, 0, 0)));
    }

    @Test void passesThroughWallsAtAnyDistance() {
        Cell goal = new Cell(2, 0, 0);
        Set<Cell> walls = Set.of(goal.offset(1, 0, 0), goal.offset(-1, 0, 0),
                goal.offset(0, 0, 1), goal.offset(0, 0, -1));
        List<Cell> through = Error404Pathfinder.find(flat(walls), new Cell(0, 0, 0), goal);
        assertEquals(goal, through.getLast());
        assertTrue(through.contains(new Cell(1, 0, 0)));
        assertEquals(new Cell(80, 0, 0),
                Error404Pathfinder.find(flat(Set.of()), new Cell(0, 0, 0), new Cell(80, 0, 0)).getLast());
        assertFalse(Error404Pathfinder.find(flat(Set.of(goal)), new Cell(0, 0, 0), goal).isEmpty());
    }

    @Test void crossesOpenSpaceAndWalls() {
        Cell start = new Cell(0, 0, 0), goal = new Cell(2, 1, 0);
        Error404Pathfinder.Grid open = new Error404Pathfinder.Grid() {
            public boolean clear(Cell cell) { return true; }
            public boolean standable(Cell cell) { return true; }
        };
        List<Cell> path = Error404Pathfinder.find(open, start, goal);
        assertEquals(start, path.getFirst());
        assertEquals(goal, path.getLast());
        assertTrue(path.size() > 2);
    }

    @Test void eachEntrantGetsOneFullSecondIncludingLateEntrants() {
        Error404Exposure exposure = new Error404Exposure();
        UUID first = UUID.randomUUID(), late = UUID.randomUUID();
        assertFalse(exposure.sample(first, true, 100));
        assertFalse(exposure.sample(first, true, 119));
        assertTrue(exposure.sample(first, true, 120));
        assertFalse(exposure.sample(late, true, 500));
        assertFalse(exposure.sample(late, true, 519));
        assertTrue(exposure.sample(late, true, 520));
    }

    @Test void leavingResetsExposureAndZonesDoNotShareTimers() {
        UUID player = UUID.randomUUID();
        Error404Exposure a = new Error404Exposure(), b = new Error404Exposure();
        assertFalse(a.sample(player, true, 0));
        assertFalse(a.sample(player, false, 19));
        assertFalse(a.contains(player));
        assertFalse(a.sample(player, true, 30));
        assertFalse(a.sample(player, true, 49));
        assertTrue(a.sample(player, true, 50));
        assertFalse(b.sample(player, true, 50));
        a.forget(player);
        assertFalse(a.contains(player));
    }

    @Test void countdownUsesCeilingAndNeverGoesNegative() {
        assertEquals(15, Error404Exposure.secondsLeft(300, 0));
        assertEquals(15, Error404Exposure.secondsLeft(300, 1));
        assertEquals(1, Error404Exposure.secondsLeft(300, 299));
        assertEquals(0, Error404Exposure.secondsLeft(300, 300));
        assertEquals(0, Error404Exposure.secondsLeft(300, 999));
    }
}

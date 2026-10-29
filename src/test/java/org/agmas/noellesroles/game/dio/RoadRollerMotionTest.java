package org.agmas.noellesroles.game.dio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoadRollerMotionTest {
    @Test
    void raisesThirtyBlocksWhenThereIsRoom() {
        assertEquals(130, RoadRollerMotion.raisedFeetY(100, 200, 320, 1.8), 1e-9);
    }

    @Test
    void respectsMapAndWorldCeilingsIncludingPlayerHeight() {
        assertEquals(118.15, RoadRollerMotion.raisedFeetY(100, 120, 320, 1.8), 1e-9);
        assertEquals(318.15, RoadRollerMotion.raisedFeetY(310, 400, 320, 1.8), 1e-9);
        assertTrue(RoadRollerMotion.raisedFeetY(119, 120, 320, 1.8) < 119);
    }

    @Test
    void holdsThenAcceleratesAndStopsExactlyAtBottomFloor() {
        assertEquals(130, RoadRollerMotion.rollerY(130, 60, 0, 10, 40));
        assertEquals(130, RoadRollerMotion.rollerY(130, 60, 10, 10, 40));
        double previous = 130;
        for (int tick = 11; tick <= 40; tick++) {
            double y = RoadRollerMotion.rollerY(130, 60, tick, 10, 40);
            assertTrue(y <= previous && y >= 60);
            previous = y;
        }
        assertEquals(60, previous);
        assertEquals(60, RoadRollerMotion.rollerY(130, 60, 100, 10, 40));
    }

    @Test
    void sweptHitDoesNotSkipPlayersBetweenFastTicks() {
        assertTrue(RoadRollerMotion.intersects(0, 0, 100, 70, -0.3, 84, -0.3, 0.3, 85.8, 0.3));
        assertFalse(RoadRollerMotion.intersects(0, 0, 100, 70, 5.01, 84, 0, 5.61, 85.8, 0.6));
        assertFalse(RoadRollerMotion.intersects(0, 0, 100, 70, 3.6, 84, 3.6, 4.2, 85.8, 4.2));
    }

    @Test
    void radiusIsSphericalAtEndsNotAnInfiniteKillColumn() {
        assertTrue(RoadRollerMotion.intersects(0, 0, 100, 70, 0, 65, 0, 0, 65, 0));
        assertFalse(RoadRollerMotion.intersects(0, 0, 100, 70, 0, 64.9, 0, 0, 64.9, 0));
        assertFalse(RoadRollerMotion.intersects(0, 0, 100, 70, 4, 65.9, 0, 4, 65.9, 0));
    }

    @Test
    void cooldownsAndGradualRestorationStayIndependent() {
        assertEquals(1800, RoadRollerMotion.BUY_COOLDOWN);
        assertEquals(3000, RoadRollerMotion.USE_COOLDOWN);
        assertEquals(100, RoadRollerMotion.RESTORE_DELAY);
        for (int count : new int[] {1, 50, 81, 1000, 30000}) {
            int budget = RoadRollerMotion.restoreBudget(count);
            assertTrue(budget >= 1);
            assertTrue(budget * 60 >= count);
            if (count > 1) assertTrue(budget < count);
        }
    }

    @Test
    void finishesAndReturnsWhileTimeStopStillHasTimeLeft() {
        int timeStopDuration = 200;
        int rollerDuration = 40;
        assertFalse(RoadRollerMotion.isFinished(rollerDuration - 1, rollerDuration));
        assertTrue(RoadRollerMotion.isFinished(rollerDuration, rollerDuration));
        assertTrue(timeStopDuration - rollerDuration > 0);
        assertEquals(60, RoadRollerMotion.rollerY(130, 60, rollerDuration, 10, rollerDuration));
        assertTrue(RoadRollerMotion.isFinished(41, rollerDuration));
    }
}

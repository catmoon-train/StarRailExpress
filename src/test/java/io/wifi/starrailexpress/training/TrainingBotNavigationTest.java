package io.wifi.starrailexpress.training;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.wifi.starrailexpress.training.TrainingBotNavigation.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainingBotNavigationTest {
    private static final Point ORIGIN = new Point(0, 0, 0);

    @Test
    void directLaneDoesNotNeedAGraph() {
        Point target = new Point(12, 0, 0);
        assertEquals(List.of(target), route(ORIGIN, target, List.of(), (a, b) -> true));
        assertEquals(List.of(), route(ORIGIN, ORIGIN, List.of(), (a, b) -> true));
    }

    @Test
    void coverRequiresReachableWaypointsInsteadOfTheWallBehindTarget() {
        Point goal = new Point(10, 0, 0);
        Passage clear = (from, to) -> {
            for (int i = 0; i <= 100; i++) {
                double t = i / 100.0;
                double x = from.x() + (to.x() - from.x()) * t;
                double z = from.z() + (to.z() - from.z()) * t;
                if (x >= 4 && x <= 6 && Math.abs(z) <= 2) return false;
            }
            return true;
        };
        List<Point> result = route(ORIGIN, goal, List.of(new Point(3, 0, -3), new Point(7, 0, -3)), clear);
        assertFalse(result.isEmpty());
        assertEquals(goal, result.getLast());
        assertNotEquals(goal, result.getFirst());
        Point previous = ORIGIN;
        for (Point next : result) {
            assertTrue(clear.clear(previous, next));
            previous = next;
        }
        assertTrue(route(ORIGIN, goal, List.of(), clear).isEmpty());
    }

    @Test
    void graphAndCollisionBudgetsStayBounded() {
        List<Point> oversized = new ArrayList<>();
        for (int i = 0; i < 10000; i++) oversized.add(new Point(i % 20, 0, i / 20.0));
        AtomicInteger probes = new AtomicInteger();
        assertTrue(route(ORIGIN, new Point(21, 0, 0), oversized, (a, b) -> {
            probes.incrementAndGet();
            return false;
        }).isEmpty());
        assertTrue(probes.get() <= MAX_LINK_TESTS);
        assertFalse(route(ORIGIN, new Point(60, 0, 0), List.of(new Point(20, 0, 0), new Point(40, 0, 0)),
                (a, b) -> true).isEmpty());
    }

    @Test
    void localObstructionChoosesAClearSideAndNeverTeleports() {
        Point goal = new Point(10, 0, 0);
        Point side = localStep(ORIGIN, goal, (a, b) -> b.z() > 0.8, 1);
        assertTrue(side.z() > 0.8);
        assertTrue(ORIGIN.distanceSquared(side) <= 0.9 * 0.9 + 0.0001);
        Point otherSide = localStep(ORIGIN, goal, (a, b) -> Math.abs(b.z()) > 0.8, -1);
        assertTrue(otherSide.z() < -0.8);
        assertEquals(ORIGIN, localStep(ORIGIN, goal, (a, b) -> false, 1));
        assertEquals(ORIGIN, localStep(ORIGIN, ORIGIN, (a, b) -> true, 1));
    }

    @Test
    void movementInputsRemainWorldAlignedWhileLookingAtOwner() {
        Point east = new Point(1, 0, 0);
        assertArrayEquals(new float[]{0, 1}, inputs(ORIGIN, east, 0, 1), 0.0001F);
        assertArrayEquals(new float[]{-1, 0}, inputs(ORIGIN, east, 90, 1), 0.0001F);
        assertArrayEquals(new float[]{1, 0}, inputs(ORIGIN, east, -90, 1), 0.0001F);
        assertArrayEquals(new float[]{0, 0}, inputs(ORIGIN, ORIGIN, 17, 1), 0.0001F);
    }

    @Test
    void gunKeepsRangeAndKnifeClosesTheDistance() {
        assertEquals(1, combatMotion(false, 9, true));
        assertEquals(0, combatMotion(false, 4, true));
        assertEquals(1, combatMotion(false, 4, false));
        assertEquals(1, combatMotion(true, 17 * 17, true));
        assertEquals(0, combatMotion(true, 12 * 12, true));
        assertEquals(-1, combatMotion(true, 4 * 4, true));
        assertEquals(1, combatMotion(true, 12 * 12, false));
    }

    @Test
    void attackRequiresSightReactionActualOwnerTraceAndRealRange() {
        assertTrue(mayAttack(true, 400, true, true, 12, 12));
        assertFalse(mayAttack(true, 400.01, true, true, 100, 12));
        assertTrue(mayAttack(false, 9, true, true, 12, 12));
        assertFalse(mayAttack(false, 9.01, true, true, 100, 12));
        assertFalse(mayAttack(true, 10, false, true, 100, 12));
        assertFalse(mayAttack(true, 10, true, false, 100, 12));
        assertFalse(mayAttack(true, 10, true, true, 11, 12));
    }
}

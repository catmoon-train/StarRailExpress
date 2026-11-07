package org.agmas.noellesroles.game.fake_steve;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FakeSteveNavigationTest {
    private record Cell(int x, int y, int z) {
        double distance(Cell other) {
            return Math.max(Math.max(Math.abs(x - other.x), Math.abs(z - other.z)), Math.abs(y - other.y));
        }
    }

    @Test
    void routesAroundWallEvenWhenFirstStepsMoveAwayFromPrey() {
        Set<Cell> floor = new HashSet<>();
        for (int x = -3; x <= 5; x++) for (int z = -4; z <= 4; z++) floor.add(new Cell(x, 0, z));
        // U 形围墙：目标在墙外，身体必须先退向唯一出口。
        floor.removeIf(p -> p.x == 2 && Math.abs(p.z) <= 3
                || p.x >= -1 && p.x <= 2 && Math.abs(p.z) == 3);
        Cell start = new Cell(1, 0, 0), goal = new Cell(4, 0, 0);
        var result = FakeStevePathSearch.find(start, goal::equals, p -> p.distance(goal),
                p -> horizontalEdges(p, floor, Map.of()), 512);
        assertTrue(result.complete());
        assertEquals(goal, result.path().getLast());
        assertTrue(result.path().stream().anyMatch(p -> p.x < start.x));
        assertTrue(result.path().stream().allMatch(floor::contains));
        assertEquals(result.path().size(), new HashSet<>(result.path()).size());
    }

    @Test
    void peopleAddCostRatherThanSealingOnlyPassage() {
        Set<Cell> narrow = Set.of(new Cell(0, 0, 0), new Cell(1, 0, 0), new Cell(2, 0, 0));
        Cell start = new Cell(0, 0, 0), goal = new Cell(2, 0, 0);
        var route = FakeStevePathSearch.find(start, goal::equals, p -> p.distance(goal),
                p -> horizontalEdges(p, narrow, Map.of(new Cell(1, 0, 0), 6.0D)), 32);
        assertTrue(route.complete());
        assertTrue(route.path().contains(new Cell(1, 0, 0)));
        Set<Cell> wide = new HashSet<>(narrow);
        wide.addAll(Set.of(new Cell(0, 0, 1), new Cell(1, 0, 1), new Cell(2, 0, 1)));
        route = FakeStevePathSearch.find(start, goal::equals, p -> p.distance(goal),
                p -> horizontalEdges(p, wide, Map.of(new Cell(1, 0, 0), 6.0D)), 32);
        assertTrue(route.complete());
        assertFalse(route.path().contains(new Cell(1, 0, 0)));
    }

    @Test
    void followsDirectedLiftToUpperFloorAndRejectsReverseTrip() {
        Cell start = new Cell(0, 0, 0), goal = new Cell(2, 8, 0);
        Map<Cell, List<FakeStevePathSearch.Edge<Cell>>> edges = new HashMap<>();
        edge(edges, start, new Cell(1, 0, 0));
        for (int y = 0; y < 8; y++) {
            assertTrue(FakeSteveTraversalPolicy.allowsVertical(FakeSteveTraversalPolicy.LIFT_UP, 1));
            edge(edges, new Cell(1, y, 0), new Cell(1, y + 1, 0));
        }
        edge(edges, new Cell(1, 8, 0), goal);
        var route = FakeStevePathSearch.find(start, goal::equals, p -> p.distance(goal),
                p -> edges.getOrDefault(p, List.of()), 64);
        assertTrue(route.complete());
        assertEquals(10, route.path().size());
        var reverse = FakeStevePathSearch.find(goal, start::equals, p -> p.distance(start),
                p -> edges.getOrDefault(p, List.of()), 64);
        assertFalse(reverse.complete());
        assertTrue(reverse.path().isEmpty());
        assertFalse(FakeSteveTraversalPolicy.allowsVertical(FakeSteveTraversalPolicy.LIFT_UP, -1));
        assertFalse(FakeSteveTraversalPolicy.allowsVertical(FakeSteveTraversalPolicy.LIFT_DOWN, 1));
        assertTrue(FakeSteveTraversalPolicy.allowsVertical(FakeSteveTraversalPolicy.LIFT_DOWN, -1));
        assertTrue(FakeSteveTraversalPolicy.allowsVertical(FakeSteveTraversalPolicy.WATER, -1));
    }

    @Test
    void searchBudgetIsStrictAndPartialRouteNeverPretendsToReachGoal() {
        var result = FakeStevePathSearch.find(0, n -> n == 1000, n -> 1000.0D - n,
                n -> List.of(new FakeStevePathSearch.Edge<>(n + 1, 1.0D)), 20);
        assertEquals(20, result.visited());
        assertFalse(result.complete());
        assertEquals(19, result.path().getLast());
    }

    @Test
    void floorAndHalfStepMustActuallyBeReached() {
        assertFalse(FakeSteveTraversalPolicy.reached(0, 3, 0));
        assertFalse(FakeSteveTraversalPolicy.reached(0.8, 0, 0));
        assertTrue(FakeSteveTraversalPolicy.reached(0.2, 0.5, 0.1));
        assertFalse(FakeSteveTraversalPolicy.swimUp(FakeSteveTraversalPolicy.LIFT_UP, 1, 5));
        assertFalse(FakeSteveTraversalPolicy.swimUp(FakeSteveTraversalPolicy.LIFT_DOWN, 5, 1));
        assertTrue(FakeSteveTraversalPolicy.swimUp(FakeSteveTraversalPolicy.WATER, 1, 5));
        assertTrue(FakeSteveTraversalPolicy.swimDown(FakeSteveTraversalPolicy.WATER, 5, 1));
        assertFalse(FakeSteveTraversalPolicy.swimDown(FakeSteveTraversalPolicy.LIFT_DOWN, 5, 1));
    }

    @Test
    void healthyCommittedRouteIsNotRebuiltJustBecauseTimerExpired() {
        assertFalse(FakeSteveTraversalPolicy.repath(false, false, false, 100, 20));
        assertFalse(FakeSteveTraversalPolicy.repath(false, false, true, 10, 20));
        assertTrue(FakeSteveTraversalPolicy.repath(false, true, false, 20, 20));
        assertTrue(FakeSteveTraversalPolicy.repath(true, false, false, 20, 20));
    }

    @Test
    void closeAndOverlappingPlayersCauseSeparationNotForwardPushing() {
        var blocked = FakeSteveCrowdAvoidance.decide(0, 0, 0, 5,
                List.of(new FakeSteveCrowdAvoidance.NearbyPlayer(0, 0.65)), 0);
        assertTrue(blocked.crowded());
        assertEquals(0, blocked.forwardScale());
        assertTrue(blocked.strafe() < 0); // 对向各自靠右，选择相反的世界方向。
        var overlap = FakeSteveCrowdAvoidance.decide(0, 0, 0, 5,
                List.of(new FakeSteveCrowdAvoidance.NearbyPlayer(0, -0.2)), 0);
        assertTrue(overlap.crowded());
        assertEquals(0, overlap.forwardScale());
        assertFalse(blocked.shouldRepath());
    }

    @Test
    void huntingPreemptsSocialStareAndLosingFocusReturnsToTasks() {
        var brain = new FakeSteveBrain();
        assertEquals(AgentMode.STARE, brain.tick(new FakeSteveBrain.PerceptionSnapshot(
                2, false, true, true, true, false, false, true, false)).mode());
        assertEquals(AgentMode.HUNT, brain.tick(new FakeSteveBrain.PerceptionSnapshot(
                2, false, false, true, true, false, false, true, true)).mode());
        assertEquals(AgentMode.DISGUISE_TASK, brain.tick(new FakeSteveBrain.PerceptionSnapshot(
                2, false, false, false, false, false, false, true, false)).mode());
    }

    @Test
    void turningCameraDoesNotRotateWorldSpaceMovement() {
        for (float desired : new float[] {0, 90, -90, 179}) {
            for (float current : new float[] {0, 45, -90, -179}) {
                var move = FakeSteveMotionPolicy.reframe(1, 0.3F, desired, current);
                double a = Math.toRadians(current), b = Math.toRadians(desired);
                assertEquals(-Math.sin(b) + Math.cos(b) * 0.3D,
                        -Math.sin(a) * move.forward() + Math.cos(a) * move.strafe(), 0.05D);
                assertEquals(Math.cos(b) + Math.sin(b) * 0.3D,
                        Math.cos(a) * move.forward() + Math.sin(a) * move.strafe(), 0.05D);
            }
        }
    }

    private static List<FakeStevePathSearch.Edge<Cell>> horizontalEdges(Cell p, Set<Cell> floor,
            Map<Cell, Double> penalties) {
        List<FakeStevePathSearch.Edge<Cell>> result = new ArrayList<>();
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Cell next = new Cell(p.x + d[0], p.y, p.z + d[1]);
            if (floor.contains(next)) result.add(new FakeStevePathSearch.Edge<>(next, 1 + penalties.getOrDefault(next, 0.0D)));
        }
        return result;
    }

    private static void edge(Map<Cell, List<FakeStevePathSearch.Edge<Cell>>> graph, Cell from, Cell to) {
        graph.computeIfAbsent(from, ignored -> new ArrayList<>()).add(new FakeStevePathSearch.Edge<>(to, 1));
    }
}

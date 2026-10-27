package io.wifi.starrailexpress.settlement.client;

import io.wifi.starrailexpress.settlement.SettlementChoreography;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 不加载 Minecraft 的物理回归测试。 */
class SettlementPhysicsTest {
    private static SettlementPhysics scene(int count) {
        var simulation = new SettlementPhysics(count, SettlementChoreography.REAR_FLOOR);
        for (int i = 0; i < count; i++) {
            var body = new SettlementPhysics.Body(0, 1, SettlementChoreography.PILE_Z, i);
            body.release(0, 9 + i * .2, SettlementChoreography.PILE_Z);
            simulation.bodies.add(body);
        }
        return simulation;
    }

    private static double[] heights(SettlementPhysics simulation) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (var body : simulation.bodies) for (var node : body.nodes) {
            min = Math.min(min, node.y); max = Math.max(max, node.y);
        }
        return new double[]{min, max};
    }

    @Test void gravityFallsAndFloorBouncesWithoutTunneling() {
        var simulation = scene(1);
        var body = simulation.bodies.getFirst();
        double start = body.nodes[1].y;
        simulation.tick();
        assertTrue(body.nodes[1].y < start);
        boolean bounced = false;
        double lowest = start;
        for (int tick = 0; tick < 150; tick++) {
            simulation.tick();
            double y = body.nodes[1].y;
            if (lowest < SettlementChoreography.REAR_FLOOR + .8 && y > lowest + .01) bounced = true;
            lowest = Math.min(lowest, y);
            for (var node : body.nodes)
                assertTrue(node.y >= SettlementChoreography.REAR_FLOOR + .40 - 1e-8, "尸体不能穿过展示台地板");
        }
        assertTrue(bounced, "落地后应出现物理回弹");
    }

    @Test void contactsCreateAnEmergentPileAndRemainFinite() {
        var simulation = scene(20);
        for (int tick = 0; tick < 350; tick++) simulation.tick();
        for (var body : simulation.bodies) {
            for (var node : body.nodes) {
                assertTrue(Double.isFinite(node.x) && Double.isFinite(node.y) && Double.isFinite(node.z));
                assertTrue(node.y >= SettlementChoreography.REAR_FLOOR + .40 - 1e-8 && node.y < 14);
            }
            var a = body.nodes[0]; var b = body.nodes[2];
            double distance = Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2) + Math.pow(a.z - b.z, 2));
            assertEquals(1.08, distance, .22, "胶囊不能无约束解体");
        }
        double[] h = heights(simulation);
        assertTrue(h[1] - h[0] > 1.2, "二十具尸体应叠成明显的多层尸堆，实际高度差=" + (h[1] - h[0]));
    }

    @Test void evenAFewBodiesFormAVisiblePile() {
        // 常见 4v4 场景只有少量败方；它们也必须叠起来，而不是平铺成一行。
        var simulation = scene(4);
        for (int tick = 0; tick < 350; tick++) simulation.tick();
        double[] h = heights(simulation);
        assertTrue(h[1] - h[0] > .6, "四具尸体也要堆出高度，实际高度差=" + (h[1] - h[0]));
        for (var body : simulation.bodies) for (var node : body.nodes) {
            assertTrue(Math.abs(node.x) <= SettlementChoreography.pileHalfWidth(4) + .01);
            assertTrue(Math.abs(node.z - SettlementChoreography.PILE_Z)
                    <= SettlementChoreography.pileHalfDepth(4) + .01);
        }
    }

    @Test void simulationIsDeterministicAndInterpolationUsesPreviousTick() {
        var first = scene(8); var second = scene(8);
        for (int tick = 0; tick < 100; tick++) { first.tick(); second.tick(); }
        for (int i = 0; i < 8; i++) for (int n = 0; n < 3; n++) {
            var a = first.bodies.get(i).nodes[n]; var b = second.bodies.get(i).nodes[n];
            assertEquals(a.x, b.x, 0); assertEquals(a.y, b.y, 0); assertEquals(a.z, b.z, 0);
            assertEquals(a.ox, a.x(0), 0); assertEquals(a.x, a.x(1), 1e-12);
            assertEquals((a.oy + a.y) / 2, a.y(.5), 1e-12);
        }
    }

    @Test void unreleasedBodiesDoNotMove() {
        var simulation = new SettlementPhysics();
        var body = new SettlementPhysics.Body(3, 8, 5, 0); simulation.bodies.add(body);
        for (int i = 0; i < 30; i++) simulation.tick();
        assertEquals(8, body.nodes[1].y, 0);
    }
}

package io.wifi.starrailexpress.settlement.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 不加载 Minecraft 的物理回归测试。 */
class SettlementPhysicsTest {
    private static SettlementPhysics scene(int count) {
        var simulation = new SettlementPhysics();
        for (int i = 0; i < count; i++) {
            var body = new SettlementPhysics.Body(0, 1, 6, i);
            body.release((i % 4 - 1.5) * .45, 8 + i * .15, 6 + (i / 4) * .2);
            simulation.bodies.add(body);
        }
        return simulation;
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
            if (lowest < 1.1 && y > lowest + .01) bounced = true;
            lowest = Math.min(lowest, y);
            for (var node : body.nodes) assertTrue(node.y >= .34 - 1e-8);
        }
        assertTrue(bounced, "落地后应出现物理回弹");
        assertTrue(body.nodes[1].y < 2);
    }

    @Test void contactsCreateAnEmergentPileAndRemainFinite() {
        var simulation = scene(20);
        for (int tick = 0; tick < 350; tick++) simulation.tick();
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (var body : simulation.bodies) {
            for (var node : body.nodes) {
                assertTrue(Double.isFinite(node.x) && Double.isFinite(node.y) && Double.isFinite(node.z));
                assertTrue(node.y >= .34 - 1e-8 && node.y < 14);
                assertTrue(Math.abs(node.x) <= 14.5 && node.z >= 2.5 && node.z <= 13.5);
                min = Math.min(min, node.y); max = Math.max(max, node.y);
            }
            var a = body.nodes[0]; var b = body.nodes[2];
            double distance = Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2) + Math.pow(a.z - b.z, 2));
            assertEquals(1.08, distance, .22, "胶囊不能无约束解体");
        }
        assertTrue(max - min > .7, "多体碰撞应堆出高度而非所有身体穿透到同一地平面");
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

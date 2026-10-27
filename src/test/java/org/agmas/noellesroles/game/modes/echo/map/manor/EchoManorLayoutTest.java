package org.agmas.noellesroles.game.modes.echo.map.manor;

import org.agmas.noellesroles.game.modes.echo.map.EchoMapConfig;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapValidator;
import org.agmas.noellesroles.game.modes.echo.map.EchoWalkGraph;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Box;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Door;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Kind;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Layout;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Marker;
import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 生成庄园的结构与路线验收：不需要启动游戏，直接把布局当成整数方块图跑一遍
 * 与开局路线准入同构的四阶段步行检查。
 *
 * <p>覆盖的失败模式：点位互相重叠、点位落在建筑里、门体不是整格、
 * 关门时能走进核心区、开门后核心区不可达、撤离门开时到不了撤离区。
 */
class EchoManorLayoutTest {
    private static final EchoManorPlan.Base BASE = new EchoManorPlan.Base(100, 64, 200);

    private static EchoMapConfig config(Layout layout) throws Exception {
        return EchoManorPlan.toConfig(layout, EchoManorPlan.DEFAULT_MAP_ID, BASE);
    }

    @Test
    void sidecarValidForEveryPlayerCount() throws Exception {
        Layout layout = EchoManorLayout.build();
        EchoMapConfig map = config(layout);
        for (int count = EchoRosterPolicy.MIN_PLAYERS; count <= EchoRosterPolicy.MAX_PLAYERS; count++) {
            assertEquals(List.of(), EchoMapValidator.validate(map, count), "N=" + count);
        }
        assertEquals(32, EchoMapValidator.spawnCapacity(map));
        assertEquals(21, map.carrierSpawns().size());
        assertEquals(6, map.interceptorSpawns().size());
        assertEquals(5, map.brokerSpawns().size());
        assertEquals(3, map.altars().size());
        assertEquals(2, map.coreGate().doors().size());
        assertEquals(6, map.crystalNodes().size());
        assertEquals(12, map.supplyCrates().size());
    }

    @Test
    void everySpawnHasClearFeetAndHeadOnSolidGround() {
        Layout layout = EchoManorLayout.build();
        var spawns = new ArrayList<>(layout.carriers());
        spawns.addAll(layout.interceptors());
        spawns.addAll(layout.brokers());
        Set<String> occupied = new HashSet<>();
        for (var spawn : spawns) {
            assertTrue(occupied.add(spawn.x() + "," + spawn.z()), "重叠出生点 " + spawn);
            assertTrue(layout.solid(spawn.x(), EchoManorLayout.FEET_Y - 1, spawn.z()), "缺少地面 " + spawn);
            assertEquals(Kind.AIR, layout.at(spawn.x(), EchoManorLayout.FEET_Y, spawn.z()), "脚部被占用 " + spawn);
            assertEquals(Kind.AIR, layout.at(spawn.x(), EchoManorLayout.FEET_Y + 1, spawn.z()), "头部被占用 " + spawn);
        }
    }

    @Test
    void beaconsAndCrystalsMatchRosterThresholds() throws Exception {
        EchoMapConfig map = config(EchoManorLayout.build());
        for (int count = EchoRosterPolicy.MIN_PLAYERS; count <= EchoRosterPolicy.MAX_PLAYERS; count++) {
            final int players = count;
            long beacons = map.beacons().stream().filter(b -> b.minPlayers() <= players).count();
            long crystals = map.crystalNodes().stream().filter(n -> n.minPlayers() <= players).count();
            assertEquals(count <= 7 ? 2 : 3, beacons, "beacons N=" + count);
            assertEquals(count <= 7 ? 4 : 6, crystals, "crystals N=" + count);
        }
    }

    @Test
    void everyObjectivePointSitsOnItsOwnMarkerBlock() throws Exception {
        Layout layout = EchoManorLayout.build();
        EchoMapConfig map = config(layout);
        List<String> problems = new ArrayList<>();
        Set<Long> used = new HashSet<>();

        record Check(String name, double x, double y, double z) { }
        List<Check> points = new ArrayList<>();
        map.beacons().forEach(b -> points.add(new Check("beacon/" + b.id(), b.pos().x(), b.pos().y(), b.pos().z())));
        map.crystalNodes().forEach(n -> points.add(new Check("crystal/" + n.id(), n.pos().x(), n.pos().y(), n.pos().z())));
        map.altars().forEach(a -> points.add(new Check("altar/" + a.id(), a.pos().x(), a.pos().y(), a.pos().z())));
        map.supplyCrates().forEach(c -> points.add(new Check("crate/" + c.id(), c.pos().x(), c.pos().y(), c.pos().z())));
        map.blackCrystalNodes().forEach(n -> points.add(new Check("black/" + n.id(), n.pos().x(), n.pos().y(), n.pos().z())));
        map.blackDropoffs().forEach(d -> points.add(new Check("drop/" + d.id(), d.pos().x(), d.pos().y(), d.pos().z())));
        points.add(new Check("broker_exit", map.brokerExit().pos().x(), map.brokerExit().pos().y(), map.brokerExit().pos().z()));
        points.add(new Check("free_exit", map.coreGate().freeExit().controlPos().x(), map.coreGate().freeExit().controlPos().y(), map.coreGate().freeExit().controlPos().z()));
        points.add(new Check("evac_control", map.evacuation().controlPos().x(), map.evacuation().controlPos().y(), map.evacuation().controlPos().z()));
        map.coreGate().doors().forEach(d -> points.add(new Check("core/" + d.id(), d.controlPos().x(), d.controlPos().y(), d.controlPos().z())));

        for (Check point : points) {
            int x = (int) Math.floor(point.x()) - BASE.x();
            int y = (int) Math.floor(point.y()) - BASE.y();
            int z = (int) Math.floor(point.z()) - BASE.z();
            Kind kind = layout.at(x, y, z);
            if (!kind.marker()) problems.add(point.name() + " -> " + kind + " @" + x + "," + y + "," + z);
            long key = ((long) x * 131 + y) * 131 + z;
            if (!used.add(key)) problems.add(point.name() + " 与其他点位重叠");
        }
        assertEquals(List.of(), problems);

        // 每个标记方块都是脚层的 part=0；部件绝不能增加目标点数量。
        Set<String> ids = new HashSet<>();
        for (Marker marker : layout.markers()) {
            assertTrue(marker.kind().marker(), marker.id());
            assertTrue(ids.add(marker.id()), "重复标记 id: " + marker.id());
            assertEquals(EchoManorLayout.FEET_Y, marker.y(), marker.id());
            assertEquals(0, layout.machineAt(marker.x(), marker.y(), marker.z()).part(), marker.id());
        }
        assertEquals(points.size(), layout.markers().size());
    }

    @Test
    void manorWalksThroughEveryGatePhase() {
        Layout layout = EchoManorLayout.build();
        List<Door> doors = layout.doors();
        int startX = layout.carriers().getFirst().x(), startZ = layout.carriers().getFirst().z();
        int phases = 4 + doors.size() * 2;
        List<String> problems = new ArrayList<>();

        List<Goal> openGoals = openGoals(layout);
        List<Goal> coreGoals = new ArrayList<>();
        layout.beacons().stream().filter(b -> b.region().equals("core"))
                .forEach(b -> coreGoals.add(new Goal("beacon/" + b.id(), b.x(), EchoManorLayout.FEET_Y, b.z(), false)));
        layout.crates().stream().filter(c -> c.quality().equals("BLACK_MARKET"))
                .forEach(c -> coreGoals.add(new Goal("crate/" + c.id(), c.x(), EchoManorLayout.FEET_Y, c.z(), false)));
        coreGoals.add(new Goal("free_exit", layout.freeControl().x(), EchoManorLayout.FEET_Y,
                layout.freeControl().z(), false));

        // 地图 canJump 可能是 true 也可能是 false，两种步行能力都必须走通且关门进不去核心。
        for (boolean jumping : new boolean[]{false, true}) for (int phase = 0; phase < phases; phase++) {
            EchoWalkGraph graph = new EchoWalkGraph(terrain(layout, phase, doors),
                    new EchoWalkGraph.Cell(startX, EchoManorLayout.FEET_Y, startZ), jumping, phase % 2 == 1, 60_000, 360_000);
            EchoWalkGraph.Status status = graph.advance(1_000_000);
            assertEquals(EchoWalkGraph.Status.COMPLETE, status, "phase " + phase + " 步行图未完成");

            boolean evacuation = phase >= 2 + doors.size() * 2;
            if (phase == 0) {
                for (EchoWalkGraph.Cell cell : graph.reached()) {
                    if (inside(layout.coreArea(), cell.x(), cell.y(), cell.z())) {
                        problems.add("关门时仍能走进核心区: " + cell);
                        break;
                    }
                }
            }
            if (evacuation) {
                boolean inZone = graph.reached().stream().anyMatch(c -> inside(layout.evacZone(), c.x(), c.y(), c.z()));
                if (!inZone) problems.add("撤离门开时到不了撤离区");
                if (!reaches(graph, layout, layout.dropAnchor().x(), EchoManorLayout.FEET_Y, layout.dropAnchor().z(), true, false)) {
                    problems.add("撤离门开时到不了掉货锚点");
                }
                continue;
            }
            boolean coreRegion = phase >= 2;
            for (Goal goal : coreRegion ? coreGoals : openGoals) {
                if (!reaches(graph, layout, goal.x(), goal.y(), goal.z(), goal.exact(), coreRegion)) {
                    problems.add("阶段" + phase + " 不可达: " + goal.id());
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void sidecarJsonRoundTripsThroughStrictParser() throws Exception {
        EchoMapConfig map = config(EchoManorLayout.build());
        String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(map) + "\n";
        assertEquals(map, org.agmas.noellesroles.game.modes.echo.map.EchoMapRepository.parse(json));
        java.nio.file.Path dump = java.nio.file.Path.of("build/manor-sidecar.json");
        if (java.nio.file.Files.isDirectory(dump.getParent())) java.nio.file.Files.writeString(dump, json);
    }

    @Test
    void layoutIsDeterministic() {
        assertArrayEquals(EchoManorLayout.build().cells(), EchoManorLayout.build().cells());
    }

    @Test
    void everyMachineHasACompleteUniqueSolidFootprintAndClearFront() {
        Layout layout = EchoManorLayout.build();
        Set<Integer> occupied = new HashSet<>();
        assertEquals(33, layout.markers().size());
        // 34 个点位：4 大(3x2x3=18) + 30 小(2x2x2=8) 的占位总和随信标数变化，改为按实际统计。
        for (Marker root : layout.markers()) {
            var machine = layout.machineAt(root.x(), root.y(), root.z()).machine();
            Set<Integer> parts = new HashSet<>();
            int roots = 0;
            boolean core = machine.model() == EchoManorMachines.Model.CORE_BEACON
                    || machine.model() == EchoManorMachines.Model.BLACK_MARKET
                    || machine.model() == EchoManorMachines.Model.FREE_EXIT;
            for (int y = 0; y < machine.model().height(); y++)
                for (int z = 0; z < machine.model().depth(); z++) for (int x = 0; x < machine.model().width(); x++) {
                    int cx = machine.x(x, z), cy = root.y() + y, cz = machine.z(x, z);
                    var cell = layout.machineAt(cx, cy, cz);
                    assertNotNull(cell, root.id());
                    assertEquals(machine, cell.machine(), root.id());
                    assertEquals(x + 3 * z + 9 * y, cell.part(), root.id());
                    assertTrue(parts.add(cell.part()), root.id());
                    assertTrue(occupied.add(Layout.index(cx, cy, cz)), root.id());
                    assertTrue(layout.solid(cx, cy, cz), root.id());
                    assertEquals(core, layout.coreArea().contains(cx + 0.5, cy + 0.5, cz + 0.5), root.id());
                    assertFalse(layout.evacZone().contains(cx + 0.5, cy + 0.5, cz + 0.5), root.id());
                    assertFalse(layout.evacDoor().contains(cx + 0.5, cy + 0.5, cz + 0.5), root.id());
                    for (Door door : layout.doors()) assertFalse(door.volume().contains(cx + 0.5, cy + 0.5, cz + 0.5), root.id());
                    if (cell.part() == 0) {
                        roots++;
                        assertEquals(root.kind(), layout.at(cx, cy, cz), root.id());
                    } else {
                        assertEquals(Kind.MACHINE_PART, layout.at(cx, cy, cz), root.id());
                        assertFalse(layout.at(cx, cy, cz).marker(), root.id());
                    }
                }
            assertEquals(1, roots, root.id());
            // 机器正面必须有一格可站立通道：地面实心、脚与头都为空。
            for (int x = 0; x < machine.model().width(); x++) {
                int cx = machine.x(x, -1), cz = machine.z(x, -1);
                String where = root.id() + " 通道 @" + cx + "," + cz;
                assertTrue(layout.solid(cx, root.y() - 1, cz), where + " 地面");
                assertFalse(layout.solid(cx, root.y(), cz), where + " 脚");
                assertFalse(layout.solid(cx, root.y() + 1, cz), where + " 头");
            }
        }
        assertEquals(layout.machineCells().keySet(), occupied);
        for (int y = 0; y < EchoManorLayout.HEIGHT; y++) for (int x = 0; x < EchoManorLayout.WIDTH; x++)
            for (int z = 0; z < EchoManorLayout.DEPTH; z++) {
                Kind kind = layout.at(x, y, z);
                boolean expected = kind.marker() || kind == Kind.MACHINE_PART;
                boolean actual = layout.machineAt(x, y, z) != null;
                assertEquals(expected, actual, "机器表不一致 @" + x + "," + y + "," + z + " kind=" + kind);
            }
    }
    @Test
    void safePointsAndCoreBoundaryRemainUntouched() {
        Layout layout = EchoManorLayout.build();
        for (var safe : List.of(layout.freeSafe(), layout.evacSafe())) {
            assertTrue(layout.solid(safe.x(), EchoManorLayout.GROUND_Y, safe.z()));
            assertFalse(layout.solid(safe.x(), EchoManorLayout.FEET_Y, safe.z()));
            assertFalse(layout.solid(safe.x(), EchoManorLayout.FEET_Y + 1, safe.z()));
        }
        assertFalse(layout.solid(layout.dropAnchor().x(), EchoManorLayout.FEET_Y, layout.dropAnchor().z()));
        // 核心边界必须按实际金库几何检查，而不是写死旧坐标：
        // 以 coreArea 的外扩一圈为壳，壳上除两扇门体外都应是实心、且不属于任何机器。
        var core = layout.coreArea();
        var doorCells = new java.util.HashSet<String>();
        for (var door : layout.doors()) {
            var v = door.volume();
            for (int x = v.minX(); x < v.maxX(); x++)
                for (int y = v.minY(); y < v.maxY(); y++)
                    for (int z = v.minZ(); z < v.maxZ(); z++) doorCells.add(x + "," + y + "," + z);
        }
        for (int y = core.minY(); y < core.maxY(); y++) {
            for (int x = core.minX() - 1; x <= core.maxX(); x++) {
                for (int z = core.minZ() - 1; z <= core.maxZ(); z++) {
                    boolean shell = x == core.minX() - 1 || x == core.maxX()
                            || z == core.minZ() - 1 || z == core.maxZ();
                    if (!shell) continue;
                    if (doorCells.contains(x + "," + y + "," + z)) continue;
                    assertTrue(layout.solid(x, y, z), "核心边界缺口 " + x + "," + y + "," + z);
                }
            }
        }
    }

    // ==================== 测试用步行图 ====================

    private record Goal(String id, int x, int y, int z, boolean exact) { }

    private static List<Goal> openGoals(Layout layout) {
        List<Goal> goals = new ArrayList<>();
        layout.beacons().stream().filter(b -> b.region().equals("open"))
                .forEach(b -> goals.add(new Goal("beacon/" + b.id(), b.x(), EchoManorLayout.FEET_Y, b.z(), false)));
        layout.crystals().forEach(n -> goals.add(new Goal("crystal/" + n.id(), n.x(), EchoManorLayout.FEET_Y, n.z(), false)));
        layout.altars().forEach(a -> goals.add(new Goal("altar/" + a.id(), a.x(), EchoManorLayout.FEET_Y, a.z(), false)));
        layout.crates().stream().filter(c -> c.quality().equals("COMMON"))
                .forEach(c -> goals.add(new Goal("crate/" + c.id(), c.x(), EchoManorLayout.FEET_Y, c.z(), false)));
        layout.blackNodes().forEach(n -> goals.add(new Goal("black/" + n.id(), n.x(), EchoManorLayout.FEET_Y, n.z(), false)));
        layout.blackDropoffs().forEach(d -> goals.add(new Goal("drop/" + d.id(), d.x(), EchoManorLayout.FEET_Y, d.z(), false)));
        layout.doors().forEach(d -> goals.add(new Goal("core/" + d.id(), d.panelX(), EchoManorLayout.FEET_Y, d.panelZ(), false)));
        goals.add(new Goal("free_safe", layout.freeSafe().x(), EchoManorLayout.FEET_Y, layout.freeSafe().z(), true));
        goals.add(new Goal("evac_safe", layout.evacSafe().x(), EchoManorLayout.FEET_Y, layout.evacSafe().z(), true));
        goals.add(new Goal("evac_control", layout.evacControl().x(), EchoManorLayout.FEET_Y, layout.evacControl().z(), false));
        goals.add(new Goal("broker_exit", layout.brokerExit().x(), EchoManorLayout.FEET_Y, layout.brokerExit().z(), false));
        for (int n = 0; n < layout.carriers().size(); n++)
            goals.add(new Goal("carrier/" + n, layout.carriers().get(n).x(), EchoManorLayout.FEET_Y, layout.carriers().get(n).z(), true));
        for (int n = 0; n < layout.interceptors().size(); n++)
            goals.add(new Goal("interceptor/" + n, layout.interceptors().get(n).x(), EchoManorLayout.FEET_Y, layout.interceptors().get(n).z(), true));
        for (int n = 0; n < layout.brokers().size(); n++)
            goals.add(new Goal("broker/" + n, layout.brokers().get(n).x(), EchoManorLayout.FEET_Y, layout.brokers().get(n).z(), true));
        return List.copyOf(goals);
    }

    private static boolean virtualAir(Layout layout, List<Door> doors, int phase, int x, int y, int z) {
        double cx = x + 0.5, cy = y + 0.5, cz = z + 0.5;
        if (phase >= 2 + doors.size() * 2) return inside(layout.evacDoor(), cx, cy, cz);
        int door = phase / 2 - 1;
        return door >= 0 && inside(doors.get(door).volume(), cx, cy, cz);
    }

    private static EchoWalkGraph.Terrain terrain(Layout layout, int phase, List<Door> doors) {
        return new EchoWalkGraph.Terrain() {
            @Override public boolean stand(EchoWalkGraph.Cell cell) {
                return clear(cell) && solid(cell.x(), cell.y() - 1, cell.z())
                        && !virtualAir(layout, doors, phase, cell.x(), cell.y() - 1, cell.z());
            }

            @Override public boolean clear(EchoWalkGraph.Cell cell) {
                return air(cell.x(), cell.y(), cell.z()) && air(cell.x(), cell.y() + 1, cell.z());
            }

            private boolean air(int x, int y, int z) {
                if (virtualAir(layout, doors, phase, x, y, z)) return true;
                return !layout.solid(x, y, z);
            }

            private boolean solid(int x, int y, int z) { return layout.solid(x, y, z); }
        };
    }

    /** 与 EchoRouteCheck.goal 同一套判定：±3 格内站位、3 格距离、区域、射线命中目标方块。 */
    private static boolean reaches(EchoWalkGraph graph, Layout layout, int x, int y, int z, boolean exact, boolean coreRegion) {
        int baseX = x, baseY = y, baseZ = z;
        int rx = exact ? 0 : 3, ry = exact ? 0 : 2, rz = exact ? 0 : 3;
        double targetX = x + 0.5, targetY = y + 0.5, targetZ = z + 0.5;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dy = -ry; dy <= ry; dy++) {
                for (int dz = -rz; dz <= rz; dz++) {
                    EchoWalkGraph.Cell cell = new EchoWalkGraph.Cell(baseX + dx, baseY + dy, baseZ + dz);
                    if (!graph.reached(cell)) continue;
                    double eyeX = cell.x() + 0.5, eyeY = cell.y() + 1.62, eyeZ = cell.z() + 0.5;
                    if (Math.pow(eyeX - targetX, 2) + Math.pow(eyeY - targetY, 2) + Math.pow(eyeZ - targetZ, 2) > 9) continue;
                    if (inside(layout.coreArea(), cell.x() + 0.5, cell.y(), cell.z() + 0.5) != coreRegion) continue;
                    int[] hit = ray(layout, eyeX, eyeY, eyeZ, targetX, targetY, targetZ);
                    if (hit == null || (hit[0] == x && hit[1] == y && hit[2] == z)) return true;
                }
            }
        }
        return false;
    }

    /** 体素步进（Amanatides-Woo）；返回第一个实心方块，null 表示没命中。 */
    private static int[] ray(Layout layout, double sx, double sy, double sz, double ex, double ey, double ez) {
        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
        int ix = (int) Math.floor(sx), iy = (int) Math.floor(sy), iz = (int) Math.floor(sz);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double tDeltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tDeltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tDeltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tMaxX = dx == 0 ? Double.MAX_VALUE : (dx > 0 ? (ix + 1 - sx) / dx : (sx - ix) / -dx);
        double tMaxY = dy == 0 ? Double.MAX_VALUE : (dy > 0 ? (iy + 1 - sy) / dy : (sy - iy) / -dy);
        double tMaxZ = dz == 0 ? Double.MAX_VALUE : (dz > 0 ? (iz + 1 - sz) / dz : (sz - iz) / -dz);
        int endX = (int) Math.floor(ex), endY = (int) Math.floor(ey), endZ = (int) Math.floor(ez);
        for (int guard = 0; guard < 512; guard++) {
            if (ix == endX && iy == endY && iz == endZ) return null;
            if (tMaxX < tMaxY && tMaxX < tMaxZ) { ix += stepX; tMaxX += tDeltaX; }
            else if (tMaxY < tMaxZ) { iy += stepY; tMaxY += tDeltaY; }
            else { iz += stepZ; tMaxZ += tDeltaZ; }
            if (layout.solid(ix, iy, iz)) return new int[]{ix, iy, iz};
        }
        return null;
    }

    private static boolean inside(Box box, double x, double y, double z) {
        return box.contains(x, y, z);
    }
}

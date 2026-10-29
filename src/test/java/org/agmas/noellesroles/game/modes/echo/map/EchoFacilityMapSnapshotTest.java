package org.agmas.noellesroles.game.modes.echo.map;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.echo.state.EchoMachineView.Type;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 公共设施的离线投影回归；不初始化游戏注册表、不启动真实 Bootstrap。 */
class EchoFacilityMapSnapshotTest {
    private String example() throws Exception {
        try (var input = getClass().getResourceAsStream("/echo-freight/example-map.json")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private EchoMapConfig world() throws Exception {
        return EchoMapRepository.parse(example()).translated(new EchoMapConfig.Vec(100, 10, 200));
    }

    private long count(List<EchoFacilityMapPayload.Entry> entries, Type type) {
        return entries.stream().filter(entry -> entry.type() == type).count();
    }

    private void point(List<EchoFacilityMapPayload.Entry> entries, Type type, EchoMapConfig.Vec pos) {
        assertEquals(1, entries.stream().filter(entry -> entry.type() == type
                && entry.x() == pos.x() && entry.y() == pos.y() && entry.z() == pos.z()).count(), type + " " + pos);
    }

    @Test void thresholdsAndPerTypeNumbersFollowOriginalMatchSize() throws Exception {
        var map = world();
        for (int players = 6; players <= 32; players++) {
            var entries = EchoFacilityMapSnapshot.project(map, players);
            assertEquals(players < 8 ? 1 : 2, count(entries, Type.BEACON));
            assertEquals(1, count(entries, Type.CORE_BEACON));
            assertEquals(players < 8 ? 4 : 6, count(entries, Type.CRYSTAL));
            assertEquals(players < 8 ? 6 : players < 9 ? 8 : 10, count(entries, Type.SUPPLY));
            assertEquals(2, count(entries, Type.BLACK_MARKET));
            assertEquals(EnumSet.allOf(Type.class), entries.stream().map(EchoFacilityMapPayload.Entry::type).collect(Collectors.toSet()));
            var numbers = new EnumMap<Type, Integer>(Type.class);
            for (var entry : entries) assertEquals(numbers.merge(entry.type(), 1, Integer::sum).intValue(), entry.number());
            assertEquals(entries, EchoFacilityMapSnapshot.project(map, players));
        }
        // 核心信标与黑市也必须遵守各自 minPlayers，不能只过滤普通同类。
        var root = JsonParser.parseString(example()).getAsJsonObject();
        root.getAsJsonArray("beacons").get(1).getAsJsonObject().addProperty("minPlayers", 32);
        root.getAsJsonArray("supplyCrates").get(10).getAsJsonObject().addProperty("minPlayers", 32);
        var late = EchoMapRepository.parse(root.toString()).translated(new EchoMapConfig.Vec(0, 0, 0));
        assertEquals(0, count(EchoFacilityMapSnapshot.project(late, 31), Type.CORE_BEACON));
        assertEquals(1, count(EchoFacilityMapSnapshot.project(late, 31), Type.BLACK_MARKET));
        assertEquals(1, count(EchoFacilityMapSnapshot.project(late, 32), Type.CORE_BEACON));
        assertEquals(2, count(EchoFacilityMapSnapshot.project(late, 32), Type.BLACK_MARKET));
    }

    @Test void allPublicPositionsAreWorldCoordinatesWithoutPrivateSpawnOrSafePoints() throws Exception {
        var map = world();
        var entries = EchoFacilityMapSnapshot.project(map, 32);
        for (var p : map.beacons()) point(entries, "core".equals(p.region()) ? Type.CORE_BEACON : Type.BEACON, p.pos());
        for (var p : map.crystalNodes()) point(entries, Type.CRYSTAL, p.pos());
        for (var p : map.altars()) point(entries, Type.ALTAR, p.pos());
        for (var p : map.supplyCrates()) point(entries, "COMMON".equals(p.quality()) ? Type.SUPPLY : Type.BLACK_MARKET, p.pos());
        for (var p : map.coreGate().doors()) point(entries, Type.CORE_CONSOLE, p.controlPos());
        point(entries, Type.FREE_EXIT, map.coreGate().freeExit().controlPos());
        point(entries, Type.EVACUATION_CONSOLE, map.evacuation().controlPos());
        // 示例区中心(0.5,66,43.5)只应用一次(100,10,200)偏移。
        point(entries, Type.EVACUATION_CONSOLE, new EchoMapConfig.Vec(100.5, 76, 243.5));
        assertEquals(2, count(entries, Type.EVACUATION_CONSOLE));
        for (var p : map.blackCrystalNodes()) point(entries, Type.BLACK_NODE, p.pos());
        for (var p : map.blackDropoffs()) point(entries, Type.BLACK_DROPOFF, p.pos());
        point(entries, Type.BROKER_EXIT, map.brokerExit().pos());
        assertEquals(34, entries.size());

        var forbidden = new ArrayList<EchoMapConfig.Vec>();
        map.carrierSpawns().forEach(p -> forbidden.add(p.pos()));
        map.interceptorSpawns().forEach(p -> forbidden.add(p.pos()));
        map.brokerSpawns().forEach(p -> forbidden.add(p.pos()));
        forbidden.add(map.spectatorPos().pos());
        forbidden.add(map.coreGate().freeExit().outsideSafe().pos());
        forbidden.add(map.evacuation().outsideSafe().pos());
        forbidden.add(map.evacuation().exitDropAnchor());
        for (var pos : forbidden) assertFalse(entries.stream().anyMatch(entry -> entry.x() == pos.x()
                && entry.y() == pos.y() && entry.z() == pos.z()), pos.toString());
        assertThrows(IllegalArgumentException.class, () -> EchoFacilityMapSnapshot.project(EchoMapRepository.parse(example()), 6));
        assertThrows(IllegalArgumentException.class, () -> EchoFacilityMapSnapshot.project(map, 5));
        assertThrows(IllegalArgumentException.class, () -> EchoFacilityMapSnapshot.project(map, 33));
    }

    private EchoMapConfig arrays(EchoMapConfig map, int size, int crystals) {
        return new EchoMapConfig(1, map.mapId(), "world", map.coreArea(), map.carrierSpawns(), map.interceptorSpawns(), map.brokerSpawns(),
                Collections.nCopies(size, map.beacons().getFirst()), Collections.nCopies(crystals, map.crystalNodes().getFirst()),
                Collections.nCopies(size, map.altars().getFirst()), Collections.nCopies(size, map.supplyCrates().getFirst()),
                new EchoMapConfig.CoreGate(Collections.nCopies(size, map.coreGate().doors().getFirst()), map.coreGate().freeExit()),
                map.evacuation(), Collections.nCopies(size, map.blackCrystalNodes().getFirst()),
                Collections.nCopies(size, map.blackDropoffs().getFirst()), map.brokerExit(), map.spectatorPos());
    }

    @Test void mapsFitProtocolUpperBoundAndOversizedConfigurationsFailInsteadOfTruncating() throws Exception {
        var map = world();
        // 侧文件每数组128项；即使放宽信标数量和祭坛恰好3个的约束也仅900项。
        var maximum = EchoFacilityMapSnapshot.project(arrays(map, 128, 128), 32);
        assertEquals(7 * 128 + 4, maximum.size());
        assertTrue(maximum.size() < EchoFacilityMapPayload.MAX_ENTRIES);
        assertThrows(UnsupportedOperationException.class, maximum::clear);
        assertThrows(IllegalArgumentException.class, () -> EchoFacilityMapSnapshot.project(arrays(map, 1, 2049), 32));
    }

    @Test void payloadAndEntriesAreImmutableAndRejectOutOfBoundsFields() {
        var dimension = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        var match = new UUID(1, 2);
        var entry = new EchoFacilityMapPayload.Entry(Type.ALTAR, 1, 64, 2, 1);
        var mutable = new ArrayList<>(List.of(entry));
        var payload = new EchoFacilityMapPayload(match, dimension, "map", mutable);
        mutable.clear();
        assertEquals(List.of(entry), payload.entries());
        assertThrows(UnsupportedOperationException.class, () -> payload.entries().clear());
        assertEquals("noellesroles:echo_facility_map", payload.type().id().toString());
        assertEquals(2048, new EchoFacilityMapPayload(match, dimension, "x".repeat(128), Collections.nCopies(2048, entry)).entries().size());
        assertThrows(IllegalArgumentException.class, () -> new EchoFacilityMapPayload(match, dimension, "x".repeat(129), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EchoFacilityMapPayload(match, dimension, " ", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EchoFacilityMapPayload(match, dimension, "map", Collections.nCopies(2049, entry)));
        assertThrows(NullPointerException.class, () -> new EchoFacilityMapPayload(null, dimension, "map", List.of()));
        assertThrows(NullPointerException.class, () -> new EchoFacilityMapPayload(match, null, "map", List.of()));
        assertThrows(NullPointerException.class, () -> new EchoFacilityMapPayload(match, dimension, null, List.of()));
        assertThrows(NullPointerException.class, () -> new EchoFacilityMapPayload.Entry(null, 0, 64, 0, 1));
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -30_000_001, 30_000_001}) {
            assertThrows(IllegalArgumentException.class, () -> new EchoFacilityMapPayload.Entry(Type.ALTAR, invalid, 64, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> new EchoFacilityMapPayload.Entry(Type.ALTAR, 0, 64, invalid, 1));
        }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -2049, 2049})
            assertThrows(IllegalArgumentException.class, () -> new EchoFacilityMapPayload.Entry(Type.ALTAR, 0, invalid, 0, 1));
        for (int invalid : new int[]{Integer.MIN_VALUE, 0, 2049, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new EchoFacilityMapPayload.Entry(Type.ALTAR, 0, 64, 0, invalid));
        assertDoesNotThrow(() -> new EchoFacilityMapPayload.Entry(Type.ALTAR, -30_000_000, -2048, 30_000_000, 2048));
        assertDoesNotThrow(() -> new EchoFacilityMapPayload.Entry(Type.ALTAR, 30_000_000, 2048, -30_000_000, 1));
        var empty = new EchoFacilityMapPayload(EchoFacilityMapPayload.NONE, dimension, "", List.of());
        assertTrue(empty.entries().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new EchoFacilityMapPayload(EchoFacilityMapPayload.NONE, dimension, "", List.of(entry)));
    }
}

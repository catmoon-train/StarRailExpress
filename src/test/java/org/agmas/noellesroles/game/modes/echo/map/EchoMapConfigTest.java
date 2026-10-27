package org.agmas.noellesroles.game.modes.echo.map;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EchoMapConfigTest {
    @TempDir Path world;

    private String example() throws IOException {
        try(var input=getClass().getResourceAsStream("/echo-freight/example-map.json")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void exampleValidForAllPlayerCountsAndCoordinatesTranslateExactlyOnce() throws Exception {
        var map=EchoMapRepository.parse(example());
        for(int count=6;count<=12;count++) assertEquals(java.util.List.of(),EchoMapValidator.validate(map,count));
        var offset=new EchoMapConfig.Vec(100,10,200);
        var moved=map.translated(offset);
        assertEquals("world",moved.coordinateSpace());
        assertEquals(map.coreArea().minX()+100,moved.coreArea().minX());
        assertEquals(map.carrierSpawns().getFirst().z()+200,moved.carrierSpawns().getFirst().z());
        assertEquals(map.coreGate().doors().getFirst().volume().minY()+10,moved.coreGate().doors().getFirst().volume().minY());
        assertEquals(map.coreGate().freeExit().outsideSafe().x()+100,moved.coreGate().freeExit().outsideSafe().x());
        assertEquals(map.evacuation().exitDropAnchor().z()+200,moved.evacuation().exitDropAnchor().z());
        assertEquals(map.blackDropoffs().getFirst().pos().y()+10,moved.blackDropoffs().getFirst().pos().y());
        assertEquals(map.brokerExit().pos().x()+100,moved.brokerExit().pos().x());
        assertThrows(IllegalStateException.class,() -> moved.translated(offset));
        assertThrows(UnsupportedOperationException.class,() -> map.beacons().clear());
    }

    @Test void legacyMapCapacityRemainsTwelveAndCannotStartThirtyTwo() throws Exception {
        var map = EchoMapRepository.parse(example());
        assertEquals(12, EchoMapValidator.spawnCapacity(map));
        assertEquals(12, EchoMapValidator.validationPlayerLimit(map));
        var errors = EchoMapValidator.validate(map, 32);
        assertTrue(errors.contains("carrierSpawns数量"));
        assertTrue(errors.contains("interceptorSpawns数量"));
        assertFalse(errors.contains("brokerSpawns数量"));
        EchoMapRepository.save(world, map, "");
        assertEquals(map, EchoMapRepository.load(world, map.mapId()));
    }

    @Test void formerTwentyFourPlayerMapStillSavesWithinItsCapacity() throws Exception {
        var map = org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorPlan.toConfig(
                org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.build(), "legacy24",
                new org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorPlan.Base(0, 64, 0));
        var root = new com.google.gson.Gson().toJsonTree(map).getAsJsonObject();
        String[] fields = {"carrierSpawns", "interceptorSpawns", "brokerSpawns"};
        int[] sizes = {16, 4, 4};
        for (int i = 0; i < fields.length; i++) {
            var spawns = root.getAsJsonArray(fields[i]);
            while (spawns.size() > sizes[i]) spawns.remove(spawns.size() - 1);
        }
        var legacy = EchoMapRepository.parse(root.toString());
        int capacity = EchoMapValidator.spawnCapacity(legacy);
        assertTrue(capacity >= 24 && capacity < 32);
        for (int count = 6; count <= capacity; count++) assertTrue(EchoMapValidator.validate(legacy, count).isEmpty());
        assertFalse(EchoMapValidator.validate(legacy, 32).isEmpty());
        EchoMapRepository.save(world, legacy, "");
        assertEquals(legacy, EchoMapRepository.load(world, legacy.mapId()));
    }

    @Test void usedSpawnPoolsLimitExpandedMapCapacityAndOverlappingSpawnsAreRejected() throws Exception {
        var map = org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorPlan.toConfig(
                org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.build(), "expanded",
                new org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorPlan.Base(0, 64, 0));
        assertEquals(32, EchoMapValidator.spawnCapacity(map));
        for (String field : new String[]{"carrierSpawns", "interceptorSpawns"}) {
            var root = new com.google.gson.Gson().toJsonTree(map).getAsJsonObject();
            var spawns = root.getAsJsonArray(field);
            spawns.remove(spawns.size() - 1);
            var limited = EchoMapRepository.parse(root.toString());
            int capacity = EchoMapValidator.spawnCapacity(limited);
            assertTrue(capacity >= 12 && capacity < 32, field);
            for (int count = 6; count <= capacity; count++) assertTrue(EchoMapValidator.validate(limited, count).isEmpty());
            assertFalse(EchoMapValidator.validate(limited, capacity + 1).isEmpty());
            EchoMapRepository.save(world, limited, EchoMapRepository.digest(world, limited.mapId()));
        }
        var draft = new EchoMapDraft(map);
        draft.mark("carrier_spawn", "1", map.carrierSpawns().getFirst().pos(), 0, 0, 6);
        assertTrue(EchoMapValidator.validate(draft.snapshot(), 32).contains("出生点互相重叠"));
        assertThrows(IOException.class, () -> EchoMapRepository.save(world, draft.snapshot(), ""));
    }

    @Test void sharedPoolAcceptsMissingEmptyAndUnusedUnsafeLegacyBrokerSpawns() throws Exception {
        var root=JsonParser.parseString(example()).getAsJsonObject();
        root.remove("brokerSpawns");
        var missing=EchoMapRepository.parse(root.toString());
        assertTrue(missing.brokerSpawns().isEmpty());
        assertEquals(12,EchoMapValidator.spawnCapacity(missing));
        for(int count=6;count<=12;count++) assertTrue(EchoMapValidator.validate(missing,count).isEmpty());
        EchoMapRepository.save(world,missing,"");
        assertEquals(missing,EchoMapRepository.load(world,missing.mapId()));
        var legacy=JsonParser.parseString(example()).getAsJsonObject();
        var broker=legacy.getAsJsonArray("brokerSpawns");
        broker.remove(1);
        broker.get(0).getAsJsonObject().addProperty("x",0.5);
        broker.get(0).getAsJsonObject().addProperty("z",0.5); // 未使用的旧出生点在核心内。
        var unused=EchoMapRepository.parse(legacy.toString());
        assertEquals(1,unused.brokerSpawns().size());
        assertEquals(12,EchoMapValidator.spawnCapacity(unused));
        for(int count=6;count<=12;count++) assertTrue(EchoMapValidator.validate(unused,count).isEmpty());
    }

    @Test void savingExpandedMapChecksTheThirtyTwoPlayerTier() throws Exception {
        var map = org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorPlan.toConfig(
                org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.build(), "expanded",
                new org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorPlan.Base(0, 64, 0));
        EchoMapRepository.save(world, map, "");
        var draft = new EchoMapDraft(map);
        draft.mark("beacon_open", "extra", new EchoMapConfig.Vec(5.5, 68.5, 90.5), 0, 0, 32);
        for (int count = 6; count < 32; count++) assertTrue(EchoMapValidator.validate(draft.snapshot(), count).isEmpty());
        var invalid = draft.snapshot();
        var error = assertThrows(IOException.class, () -> EchoMapRepository.save(world, invalid, EchoMapRepository.digest(world, map.mapId())));
        assertTrue(error.getMessage().startsWith("N=32:"));
        assertEquals(map, EchoMapRepository.load(world, map.mapId()));
    }

    @Test void strictParserRejectsMissingMalformedTrailingDuplicateAndNonfinite() throws Exception {
        String json=example();
        assertThrows(IOException.class,() -> EchoMapRepository.parse(json+" {}"));
        assertThrows(IOException.class,() -> EchoMapRepository.parse(json.replace("\"schemaVersion\": 1","\"schemaVersion\": 2")));
        assertThrows(IOException.class,() -> EchoMapRepository.parse(json.replace("\"schemaVersion\": 1","\"schemaVersion\": 4294967297")));
        assertThrows(IOException.class,() -> EchoMapRepository.parse("{\"schemaVersion\":1}"));
        assertThrows(IOException.class,() -> EchoMapRepository.parse("{broken}"));
        assertThrows(IOException.class,() -> EchoMapRepository.parse(json.replace("\"schemaVersion\": 1","\"schemaVersion\":1,\"schemaVersion\":1")));
        assertThrows(IOException.class,() -> EchoMapRepository.parse(json.replace("-45.5","NaN")));
        var root=JsonParser.parseString(json).getAsJsonObject();
        root.getAsJsonArray("carrierSpawns").get(0).getAsJsonObject().remove("x");
        assertThrows(IOException.class,() -> EchoMapRepository.parse(root.toString()));
    }

    @Test void fileLoadingRejectsTraversalMismatchAndLargeFiles() throws Exception {
        Path mapFile=world.resolve("echo_freight/maps/season1/echo_station.json");
        Files.createDirectories(mapFile.getParent());
        Files.writeString(mapFile,example());
        assertEquals("season1/echo_station",EchoMapRepository.load(world,"season1/echo_station").mapId());
        for(String id:new String[]{"../escape","/absolute","a//b","a/../b","a\\b","a:b","UPPER","a/./b"}) {
            assertThrows(IOException.class,() -> EchoMapRepository.load(world,id));
        }
        Files.writeString(mapFile,example().replace("season1/echo_station","different"));
        assertThrows(IOException.class,() -> EchoMapRepository.load(world,"season1/echo_station"));
        Files.writeString(mapFile," ".repeat(1024*1024+1));
        assertThrows(IOException.class,() -> EchoMapRepository.load(world,"season1/echo_station"));
    }

    @Test void duplicatePointsAndWrongRegionReportSpecificErrors() throws Exception {
        var root=JsonParser.parseString(example()).getAsJsonObject();
        root.getAsJsonArray("altars").get(1).getAsJsonObject().addProperty("id","a1");
        root.getAsJsonArray("beacons").get(0).getAsJsonObject().addProperty("region","core");
        var errors=EchoMapValidator.validate(EchoMapRepository.parse(root.toString()),6);
        assertTrue(errors.stream().anyMatch(s -> s.contains("altars")));
        assertTrue(errors.stream().anyMatch(s -> s.contains("beacons.outer_a")));
    }
}

package org.agmas.noellesroles.game.modes.echo.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class EchoMapDraftTest {
    @TempDir Path world;
    private EchoMapConfig example() throws IOException {
        try(var input=getClass().getResourceAsStream("/echo-freight/example-map.json")) {
            assertNotNull(input); return EchoMapRepository.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    @Test void editsReplaceExactPointPreserveOthersAndFailuresDoNotChangeDraft() throws Exception {
        var source=example(); var draft=new EchoMapDraft(source);
        var point=new EchoMapConfig.Vec(-46.5,64,35.5); draft.mark("carrier_spawn","0",point,45,10,6);
        assertEquals(point,draft.snapshot().carrierSpawns().getFirst().pos()); assertEquals(45,draft.snapshot().carrierSpawns().getFirst().yaw());
        assertEquals(source.carrierSpawns().get(1),draft.snapshot().carrierSpawns().get(1));
        var before=draft.snapshot(); long revision=draft.revision();
        assertThrows(IllegalArgumentException.class,() -> draft.mark("carrier_spawn","99",point,0,0,6));
        assertThrows(IllegalArgumentException.class,() -> draft.mark("unknown","x",point,0,0,6));
        assertThrows(IllegalArgumentException.class,() -> draft.mark("altar","../bad",point,0,0,6));
        assertEquals(before,draft.snapshot()); assertEquals(revision,draft.revision());
    }
    @Test void doorVolumeAndPointMetadataExportInTemplateSpaceOnly() throws Exception {
        var draft=new EchoMapDraft(example());
        draft.mark("supply","s9",new EchoMapConfig.Vec(-15.5,64,25.5),0,0,9);
        assertEquals(9,draft.snapshot().supplyCrates().stream().filter(p -> p.id().equals("s9")).findFirst().orElseThrow().minPlayers());
        var box=new EchoMapConfig.Box(-12,64,-2,-11,67,3); draft.volume("core_door","west",box);
        assertEquals(box,draft.snapshot().coreGate().doors().getFirst().volume());
        var output=draft.exported("season2/station"); assertEquals("season2/station",output.mapId()); assertEquals("template",output.coordinateSpace());
        assertEquals("season1/echo_station",draft.snapshot().mapId());
        assertThrows(IOException.class,() -> new EchoMapDraft(output.translated(new EchoMapConfig.Vec(0,0,200))));
    }
    @Test void thresholdsAcceptThroughThirtyTwoAndRejectOutsideRangeAtomically() throws Exception {
        var draft = new EchoMapDraft(example());
        for (String kind : new String[]{"beacon_open", "crystal", "supply"}) {
            draft.mark(kind, "expanded", new EchoMapConfig.Vec(-20.5, 64, 20.5), 0, 0, 13);
            draft.meta(kind, "expanded", null, 32, null);
            assertNotNull(draft.snapshot());
        }
        var before = draft.snapshot();
        long revision = draft.revision();
        for (int invalid : new int[]{5, 33}) {
            assertThrows(IllegalArgumentException.class, () -> draft.mark("crystal", "bad", new EchoMapConfig.Vec(0, 64, 0), 0, 0, invalid));
            assertThrows(IllegalArgumentException.class, () -> draft.meta("crystal", "expanded", null, invalid, null));
            assertThrows(IllegalArgumentException.class, () -> new EchoMapConfig.Node("bad", new EchoMapConfig.Vec(0, 64, 0), invalid));
        }
        assertEquals(before, draft.snapshot());
        assertEquals(revision, draft.revision());
        assertEquals(32, draft.snapshot().crystalNodes().getLast().minPlayers());
        assertEquals(32, draft.snapshot().beacons().getLast().minPlayers());
        assertEquals(32, draft.snapshot().supplyCrates().getLast().minPlayers());
    }

    @Test void newDraftIsEditableButCannotBeSavedBeforeAllRequiredPointsExist() throws Exception {
        var draft=EchoMapDraft.empty("station/new");
        draft.mark("core_control","west",new EchoMapConfig.Vec(-14,65,0),0,0,6);
        draft.volume("core_door","west",new EchoMapConfig.Box(-12,64,-1,-11,67,2));
        assertEquals(1,draft.snapshot().coreGate().doors().size());
        assertThrows(IOException.class,() -> EchoMapRepository.save(world,draft.snapshot(),""));
        assertFalse(Files.exists(world.resolve("echo_freight/maps/station/new.json")));
    }
    @Test void saveRoundTripsAndOverwriteRequiresCurrentFileDigest() throws Exception {
        var source=example(); Path file=EchoMapRepository.save(world,source,""); assertEquals(source,EchoMapRepository.load(world,source.mapId()));
        String original=Files.readString(file,StandardCharsets.UTF_8),digest=EchoMapRepository.digest(world,source.mapId()); assertEquals(64,digest.length());
        assertThrows(IOException.class,() -> EchoMapRepository.save(world,source,"")); assertEquals(original,Files.readString(file,StandardCharsets.UTF_8));
        var draft=new EchoMapDraft(source); draft.mark("carrier_spawn","0",new EchoMapConfig.Vec(-46.5,64,35.5),0,0,6);
        EchoMapRepository.save(world,draft.snapshot(),digest); assertEquals(draft.snapshot(),EchoMapRepository.load(world,source.mapId()));
        assertThrows(IOException.class,() -> EchoMapRepository.save(world,source,digest));
        try(var files=Files.list(file.getParent())) { assertTrue(files.noneMatch(p -> p.toString().endsWith(".tmp"))); }
    }
    @Test void exportRejectsUnsafeIdsAndWorldCoordinatesWithoutWriting() throws Exception {
        var source=example();
        for(String id:new String[]{"../escape","a//b","/absolute","a:b","a\\b","a/../b"}) {
            assertThrows(IOException.class,() -> EchoMapRepository.digest(world,id));
            assertThrows(IOException.class,() -> new EchoMapDraft(source).exported(id));
        }
        assertThrows(IOException.class,() -> EchoMapRepository.save(world,source.translated(new EchoMapConfig.Vec(0,0,200)),""));
        assertFalse(Files.exists(world.resolve("echo_freight")));
    }
}

package org.agmas.noellesroles.game.modes.echo.editor;

import org.agmas.noellesroles.game.modes.echo.content.EchoMachines;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapConfig;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapDraft;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 机器识别映射与自动 id：不启动注册表，不信客户端坐标。 */
class EchoEditorMachineIdsTest {
    @Test
    void generatedIdsStayUniqueBeyondOneHundredTwentyEightAndKeepTheirSuffix() {
        var taken=new ArrayList<String>();
        for(int n=1;n<=130;n++) taken.add("supply_"+n);
        taken.add("supply_x");
        assertEquals("supply_131",EchoEditorMachineIds.nextId("supply",taken));
        String stem="a".repeat(64), first=EchoEditorMachineIds.nextId(stem,List.of());
        String second=EchoEditorMachineIds.nextId(stem,List.of(first));
        assertEquals(64,first.length()); assertNotEquals(first,second); assertTrue(second.endsWith("_2"));
    }

    @Test
    void twelveMachinesMapToDraftKindsAndLanguagesMatch() throws Exception {
        assertEquals(12, EchoMachines.Kind.values().length);
        var seen = new java.util.HashSet<String>();
        for (var machine : EchoMachines.Kind.values()) {
            String kind = EchoEditorMachineIds.kindOf(machine);
            assertNotNull(kind, machine.name());
            assertTrue(EchoMapDraft.KINDS.contains(kind), kind);
            assertTrue(seen.add(kind), kind);
        }
        assertNull(EchoEditorMachineIds.kindOf(null));
        assertEquals(EnumSet.allOf(EchoMachines.Kind.class).size(), seen.size());
        var keys = Files.readString(Path.of("src/main/resources/assets/echo_editor_machine/lang/zh_cn.json"));
        for (String language : new String[]{"zh_cn", "zh_tw", "en_us"}) {
            String text = Files.readString(Path.of("src/main/resources/assets/echo_editor_machine/lang/" + language + ".json"));
            assertTrue(text.contains("\"echo.editor.machine.identify\""));
            assertTrue(text.contains("\"echo.editor.machine.not_machine\""));
            assertTrue(text.contains("\"echo.editor.machine.marked\""));
            assertTrue(text.contains("\"echo.editor.machine.updated\""));
            assertEquals(keys.lines().filter(line -> line.contains("\"echo.editor.machine.")).count(),
                    text.lines().filter(line -> line.contains("\"echo.editor.machine.")).count(), language);
        }
    }

    @Test
    void sameBlockReturnsExistingIdInsteadOfAnotherPoint() {
        var center = new EchoMapConfig.Vec(10.5, 64.5, -3.5);
        var points = List.of(
                new EchoMapDraft.PointInfo("altar_1", 0, 4.5, 64.5, -3.5),
                new EchoMapDraft.PointInfo("altar_2", 0, 10.4, 64.6, -3.4));
        assertEquals("altar_2", EchoEditorMachineIds.existingId(points, center));
        assertNull(EchoEditorMachineIds.existingId(points, new EchoMapConfig.Vec(11.2, 64.5, -3.5)));
        assertEquals("altar_2", EchoEditorMachineIds.chooseId("", "altar", points, center) == null
                ? null : EchoEditorMachineIds.existingId(points, center));
        assertFalse(EchoEditorMachineIds.sameBlock(11,64,-3.5,center));
        assertTrue(EchoEditorMachineIds.sameBlock(10,64,-3.01,center));
        assertFalse(EchoEditorMachineIds.sameBlock(10.5,64.5,-3,center));
    }

    @Test
    void blankIdSkipsTakenNamesAndManualIdMustNotStealAnotherPoint() {
        var center = new EchoMapConfig.Vec(1.5, 64.5, 2.5);
        var points = List.of(
                new EchoMapDraft.PointInfo("crystal_1", 6, 8.5, 64.5, 2.5),
                new EchoMapDraft.PointInfo("crystal_2", 6, 9.5, 64.5, 2.5));
        assertEquals("crystal_3", EchoEditorMachineIds.chooseId("  ", "crystal", points, center));
        assertEquals("crystal_1", EchoEditorMachineIds.chooseId("Crystal_1", "crystal", points,
                new EchoMapConfig.Vec(8.5, 64.5, 2.5)));
        assertNull(EchoEditorMachineIds.chooseId("crystal_1", "crystal", points, center));
        assertNull(EchoEditorMachineIds.chooseId("Bad ID", "crystal", points, center));
        assertEquals("beacon_1", EchoEditorMachineIds.nextId("beacon", List.of()));
        var taken = new ArrayList<String>();
        taken.add("supply_1");
        assertEquals("supply_2", EchoEditorMachineIds.nextId(EchoEditorMachineIds.prefix("supply"), taken));
        assertTrue(EchoEditorMachineIds.hasPlayers("black_market"));
        assertFalse(EchoEditorMachineIds.hasPlayers("altar"));
        assertFalse(EchoEditorMachineIds.hasPlayers("evacuation_control"));
    }
}

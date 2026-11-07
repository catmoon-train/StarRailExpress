package io.wifi.srepackcompat.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChlorideConfigSnapshotTest {
    public enum Mode { VANILLA, BLACK }
    public static class Group {
        public boolean enabled;
        public int distance;
        public double brightness;
        public Mode mode = Mode.BLACK;
        public List<String> disabled = new ArrayList<>();
    }
    public static class Config {
        public static boolean modpackMode;
        public static final Group fog = new Group();
        public static final Group ui = new Group();
    }

    @BeforeEach void reset() {
        Config.modpackMode = true;
        Config.fog.enabled = false;
        Config.fog.distance = 0;
        Config.fog.brightness = 1;
        Config.fog.mode = Mode.BLACK;
        Config.fog.disabled = new ArrayList<>(List.of("bad"));
        Config.ui.enabled = false;
    }

    private Map<String, Object> entries() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modpackMode", "false");
        result.put("fog.enabled", "true");
        result.put("fog.distance", "4_096");
        result.put("fog.brightness", "0.5");
        result.put("fog.mode", "VANILLA");
        result.put("fog.disabled", new String[]{"minecraft:smoke"});
        result.put("interface.enabled", "true");
        return result;
    }

    @Test void loadsAllTypesAndInterfaceAliasWithoutReplacingGroups() throws Exception {
        Group original = Config.fog;
        var snapshot = new ChlorideConfigSnapshot(Config.class, entries(), s -> s);
        assertEquals(7, snapshot.size());
        assertEquals(7, snapshot.restore());
        assertSame(original, Config.fog);
        assertFalse(Config.modpackMode);
        assertTrue(Config.fog.enabled);
        assertEquals(4096, Config.fog.distance);
        assertEquals(0.5, Config.fog.brightness);
        assertEquals(Mode.VANILLA, Config.fog.mode);
        assertEquals(List.of("minecraft:smoke"), Config.fog.disabled);
        assertTrue(Config.ui.enabled);
        assertEquals(0, snapshot.restore());
    }

    @Test void restoresDirectMutationsAndPreservesMutableListReference() throws Exception {
        var snapshot = new ChlorideConfigSnapshot(Config.class, entries(), s -> s);
        snapshot.restore();
        var originalList = Config.fog.disabled;
        Config.fog.enabled = false;
        Config.fog.distance = -1;
        Config.fog.brightness = Double.NaN;
        originalList.clear();
        assertEquals(4, snapshot.restore());
        assertSame(originalList, Config.fog.disabled);
        assertEquals(List.of("minecraft:smoke"), originalList);
        assertEquals(0.5, Config.fog.brightness);
    }

    @Test void repairsNullAndImmutableListsWithMutableCopies() throws Exception {
        var snapshot = new ChlorideConfigSnapshot(Config.class, entries(), s -> s);
        Config.fog.disabled = null;
        snapshot.restore();
        Config.fog.disabled.add("bad");
        snapshot.restore();
        Config.fog.disabled = List.of("other");
        snapshot.restore();
        Config.fog.disabled.add("allowed-to-mutate-before-next-enforcement");
        snapshot.restore();
        assertEquals(List.of("minecraft:smoke"), Config.fog.disabled);
    }

    @Test void snapshotDoesNotShareTheParsersArray() throws Exception {
        var entries = entries();
        String[] raw = (String[]) entries.get("fog.disabled");
        var snapshot = new ChlorideConfigSnapshot(Config.class, entries, s -> s.toUpperCase(java.util.Locale.ROOT));
        raw[0] = "changed";
        snapshot.restore();
        assertEquals(List.of("MINECRAFT:SMOKE"), Config.fog.disabled);
    }

    @Test void rejectsUnknownMissingFinalAndMalformedFields() {
        assertThrows(NoSuchFieldException.class,
                () -> new ChlorideConfigSnapshot(Config.class, Map.of("fog.missing", "true"), s -> s));
        assertThrows(IllegalArgumentException.class,
                () -> new ChlorideConfigSnapshot(Config.class, Map.of("fog", "true"), s -> s));
        for (var invalid : List.of(Map.<String,Object>of(), Map.<String,Object>of("fog.enabled", "yes"),
                Map.<String,Object>of("fog.mode", "UNKNOWN"), Map.<String,Object>of("fog.brightness", "NaN"),
                Map.<String,Object>of("fog.distance", "invalid"), Map.<String,Object>of("fog.disabled", "not-an-array"))) {
            assertThrows(IllegalArgumentException.class, () -> new ChlorideConfigSnapshot(Config.class, invalid, s -> s));
        }
    }
}

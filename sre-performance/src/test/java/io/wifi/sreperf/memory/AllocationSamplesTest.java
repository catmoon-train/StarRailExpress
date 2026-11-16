package io.wifi.sreperf.memory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AllocationSamplesTest {
    @Test void boundsTablesButKeepsTotalsAndExistingKeys() {
        AllocationSamples samples = new AllocationSamples(1);
        samples.add("Pokemon", List.of("a"), "mod:cobblemon", 10, false);
        samples.add("Other", List.of("b"), "unattributed", 30, true);
        samples.add("Pokemon", List.of("a"), "mod:cobblemon", 20, false);
        Map<String, Object> out = samples.snapshot();
        assertEquals(3L, out.get("samples"));
        assertEquals(60L, out.get("sampleWeightBytes"));
        assertEquals(1L, out.get("droppedClassSamples"));
        assertEquals(1L, out.get("droppedStackSamples"));
        assertEquals(1L, out.get("truncatedStacks"));
        List<?> rows = (List<?>) out.get("classes");
        assertEquals(1, rows.size());
        assertEquals(30L, ((Map<?, ?>) rows.getFirst()).get("sampleWeightBytes"));
        assertTrue(out.get("measurement").toString().contains("NOT retained"));
    }

    @Test void classAndStackAggregationsAreIndependentAndOrderedByWeight() {
        AllocationSamples samples = new AllocationSamples(3);
        samples.add("A", List.of(), "unattributed", 2, false);
        samples.add("B", List.of("one"), "mod:mega_showdown", 5, false);
        samples.add("B", List.of("two"), "mod:mega_showdown", 3, false);
        Map<String, Object> out = samples.snapshot();
        assertEquals(2, ((List<?>) out.get("classes")).size());
        assertEquals(3, ((List<?>) out.get("stacks")).size());
        assertEquals("B", ((Map<?, ?>) ((List<?>) out.get("classes")).getFirst()).get("class"));
    }

    @Test void rejectsInvalidCapacityAndWeightsAndKeepsEmptyExplicit() {
        assertThrows(IllegalArgumentException.class, () -> new AllocationSamples(0));
        AllocationSamples samples = new AllocationSamples(2);
        assertThrows(IllegalArgumentException.class, () -> samples.add("A", List.of(), "unknown", -1, false));
        assertEquals(0L, samples.snapshot().get("samples"));
        assertEquals(List.of(), samples.snapshot().get("classes"));
    }
}

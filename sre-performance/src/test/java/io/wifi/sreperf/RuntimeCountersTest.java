package io.wifi.sreperf;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeCountersTest {
    @Test void reportsCaptureDurationAndHeapWithoutJvmBootstrap() {
        long start = System.nanoTime();
        var counters = new RuntimeCounters(start, Thread.currentThread());
        var result = counters.finish(start + 10_000_000L);
        assertEquals(10.0, result.get("durationMs"));
        assertTrue(result.containsKey("renderThreadCpuMs"));
        assertFalse(((List<?>) result.get("heapSamples")).isEmpty());
    }

    @Test void memorySamplesAreBounded() {
        long start = System.nanoTime();
        var counters = new RuntimeCounters(start, Thread.currentThread());
        for (int i = 0; i < 400; i++) counters.sample(start + i);
        assertTrue(((List<?>) counters.finish(start + 500).get("heapSamples")).size() <= 256);
    }

    @Test void gcCountersAreDeltasNotStartupTotals() {
        var counters = new RuntimeCounters(System.nanoTime(), Thread.currentThread());
        var data = counters.finish(System.nanoTime());
        for (Object item : (List<?>) data.get("collectors")) {
            var row = (Map<?, ?>) item;
            Object count = row.get("collectionsDuringCapture");
            assertTrue(count == null || ((Number) count).longValue() >= 0);
            assertFalse(row.containsKey("totalCollections"));
        }
    }
}

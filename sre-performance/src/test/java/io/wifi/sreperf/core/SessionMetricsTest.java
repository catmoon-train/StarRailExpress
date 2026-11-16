package io.wifi.sreperf.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
class SessionMetricsTest {
    private static final long MS = 1_000_000L;

    @Test
    void emptySessionHasNullMeasurementsAndStableKeys() {
        Map<String, Object> snapshot = new SessionMetrics(2).snapshot();
        assertTrue(snapshot.keySet().containsAll(Set.of("summary", "foregroundWorld", "phases", "frames",
                "phaseMismatches", "depthOverflows", "phaseOverflows")));
        Map<String, Object> summary = map(snapshot.get("summary"));
        assertEquals(0, number(summary, "frameCount"));
        assertEquals(0, number(summary, "intervalCount"));
        for (String key : List.of("averageFps", "p50Ms", "p95Ms", "p99Ms", "maxMs", "totalIntervalMs")) {
            assertTrue(summary.containsKey(key));
            assertNull(summary.get(key));
        }
        assertTrue(rows(snapshot, "frames").isEmpty());
        assertTrue(rows(snapshot, "phases").isEmpty());
    }

    @Test
    void countsFramesSeparatelyFromStartToStartIntervalsAndWork() {
        SessionMetrics metrics = new SessionMetrics(3);
        metrics.frameStart(1_000 * MS, true, true, false);
        assertEquals(0, metrics.frameCount());
        metrics.frameEnd(1_002 * MS);
        Map<String, Object> first = metrics.snapshot();
        assertEquals(1, metrics.frameCount());
        assertNull(map(first.get("summary")).get("averageFps"));
        assertNull(rows(first, "frames").getFirst().get("intervalMs"));
        frame(metrics, 1_010, 2, true, true, false);
        frame(metrics, 1_030, 5, true, true, false);
        Map<String, Object> snapshot = metrics.snapshot();
        Map<String, Object> summary = map(snapshot.get("summary"));
        assertEquals(3, number(summary, "frameCount"));
        assertEquals(2, number(summary, "intervalCount"));
        assertEquals(2e3 / 30, decimal(summary, "averageFps"), 1e-10);
        assertEquals(30, decimal(summary, "totalIntervalMs"), 1e-10);
        List<Map<String, Object>> frames = rows(snapshot, "frames");
        assertEquals(10, decimal(frames.get(1), "startOffsetMs"));
        assertEquals(20, decimal(frames.get(2), "intervalMs"));
        assertEquals(5, decimal(frames.get(2), "workMs"));
    }

    @Test
    void percentilesUseNearestRankOfIntervalsNotWork() {
        SessionMetrics metrics = new SessionMetrics(101);
        long start = 0;
        frame(metrics, start, 0, true, true, false);
        for (int interval = 1; interval <= 100; interval++) {
            start += interval;
            frame(metrics, start, 0, true, true, false);
        }
        Map<String, Object> summary = map(metrics.snapshot().get("summary"));
        assertEquals(100, number(summary, "intervalCount"));
        assertEquals(50, decimal(summary, "p50Ms"));
        assertEquals(95, decimal(summary, "p95Ms"));
        assertEquals(99, decimal(summary, "p99Ms"));
        assertEquals(100, decimal(summary, "maxMs"));
        assertEquals(5050, decimal(summary, "totalIntervalMs"));
        assertEquals(50, number(summary, "over50Ms"));
        assertEquals(0, number(summary, "over100Ms"));
    }

    @Test
    void longFrameThresholdsAreStrict() {
        SessionMetrics metrics = new SessionMetrics(4);
        for (long start : new long[]{0, 50, 150, 251}) {
            frame(metrics, start, 0, true, true, false);
        }
        Map<String, Object> summary = map(metrics.snapshot().get("summary"));
        assertEquals(2, number(summary, "over50Ms"));
        assertEquals(1, number(summary, "over100Ms"));
    }

    @Test
    void foregroundIntervalsRequireBothAdjacentContexts() {
        SessionMetrics metrics = new SessionMetrics(11);
        long[] starts = {0, 10, 110, 210, 220, 320, 420, 430, 530, 630, 640};
        for (int i = 0; i < starts.length; i++) {
            frame(metrics, starts[i], 1, i != 2, i != 8, i == 5);
        }
        Map<String, Object> snapshot = metrics.snapshot();
        Map<String, Object> foreground = map(snapshot.get("foregroundWorld"));
        assertEquals(8, number(foreground, "frameCount"));
        assertEquals(4, number(foreground, "intervalCount"));
        assertEquals(100, decimal(foreground, "averageFps"));
        assertEquals(10, decimal(foreground, "maxMs"));
        assertEquals(10, number(map(snapshot.get("summary")), "intervalCount"));
        List<Map<String, Object>> frames = rows(snapshot, "frames");
        assertEquals(false, frames.get(2).get("focused"));
        assertEquals(true, frames.get(5).get("screenOpen"));
        assertEquals(false, frames.get(8).get("inWorld"));
    }

    @Test
    void nestedScopesConserveSelfTimeIncludingAutomaticRoot() {
        SessionMetrics metrics = new SessionMetrics(1);
        metrics.frameStart(0, true, true, false);
        long a = metrics.begin("A", 2 * MS);
        long b = metrics.begin("B", 5 * MS);
        metrics.end("B", b, 10 * MS, 32);
        metrics.end("A", a, 18 * MS, 64);
        metrics.frameEnd(20 * MS);
        Map<String, Object> snapshot = metrics.snapshot();
        assertPhase(snapshot, "FRAME", 1, 20, 4, 20, 0);
        assertPhase(snapshot, "A", 1, 16, 11, 16, 64);
        assertPhase(snapshot, "B", 1, 5, 5, 5, 32);
        assertConserved(snapshot, 20);
    }

    @Test
    void recursiveNamesHaveDistinctTokensAndNonDuplicatedFrameIntervals() {
        SessionMetrics metrics = new SessionMetrics(1);
        metrics.frameStart(0, true, true, false);
        long outer = metrics.begin("A", 2 * MS);
        long inner = metrics.begin("A", 5 * MS);
        assertNotEquals(outer, inner);
        metrics.end("A", inner, 10 * MS, 8);
        metrics.end("A", outer, 18 * MS, 16);
        metrics.frameEnd(20 * MS);
        Map<String, Object> snapshot = metrics.snapshot();
        assertPhase(snapshot, "A", 2, 21, 16, 16, 24);
        assertEquals(16, decimal(map(rows(snapshot, "frames").getFirst().get("phasesMs")), "A"));
        assertConserved(snapshot, 20);
    }

    @Test
    void recursiveNameSeparatedByOtherPhaseStillUsesUnion() {
        SessionMetrics metrics = new SessionMetrics(1);
        metrics.frameStart(0, true, true, false);
        long outer = metrics.begin("A", MS);
        long middle = metrics.begin("B", 3 * MS);
        long inner = metrics.begin("A", 5 * MS);
        metrics.end("A", inner, 10 * MS, 0);
        metrics.end("B", middle, 17 * MS, 0);
        metrics.end("A", outer, 19 * MS, 0);
        metrics.frameEnd(20 * MS);
        Map<String, Object> snapshot = metrics.snapshot();
        assertPhase(snapshot, "A", 2, 23, 9, 18, 0);
        assertPhase(snapshot, "B", 1, 14, 9, 14, 0);
        assertEquals(18, decimal(map(rows(snapshot, "frames").getFirst().get("phasesMs")), "A"));
        assertConserved(snapshot, 20);
    }

    @Test
    void zeroUnknownStaleAndWrongNameTokensAreHandledWithoutPoppingWrongScope() {
        SessionMetrics metrics = new SessionMetrics(2);
        assertEquals(0, metrics.begin("A", 0));
        metrics.end("A", 0, 0, 0);
        metrics.frameStart(0, true, true, false);
        long token = metrics.begin("A", MS);
        metrics.end("A", token + 1, 2 * MS, 0);
        metrics.end("B", token, 2 * MS, 0);
        metrics.end("A", 0, Long.MAX_VALUE, -1);
        metrics.end("A", token, 3 * MS, 4);
        metrics.end("A", token, 4 * MS, 0);
        metrics.frameEnd(5 * MS);
        metrics.end("A", token, 6 * MS, 0);
        Map<String, Object> snapshot = metrics.snapshot();
        assertEquals(4, number(snapshot, "phaseMismatches"));
        assertPhase(snapshot, "A", 1, 2, 2, 2, 4);
        assertConserved(snapshot, 5);
    }

    @Test
    void outOfOrderAndUnclosedScopesAreTruncatedAndCounted() {
        SessionMetrics metrics = new SessionMetrics(1);
        metrics.frameStart(0, true, true, false);
        long a = metrics.begin("A", MS);
        long b = metrics.begin("B", 2 * MS);
        metrics.end("A", a, 5 * MS, 0);
        metrics.end("B", b, 6 * MS, 0);
        metrics.begin("C", 7 * MS);
        metrics.frameEnd(10 * MS);
        Map<String, Object> snapshot = metrics.snapshot();
        assertEquals(3, number(snapshot, "phaseMismatches"));
        assertPhase(snapshot, "A", 1, 4, 1, 4, 0);
        assertPhase(snapshot, "B", 1, 3, 3, 3, 0);
        assertPhase(snapshot, "C", 1, 3, 3, 3, 0);
        assertConserved(snapshot, 10);
    }

    @Test
    void regressingClockAndInvalidStartIntervalsAreCountedNotMadeNegative() {
        SessionMetrics metrics = new SessionMetrics(3);
        metrics.frameStart(0, true, true, false);
        long token = metrics.begin("A", 2 * MS);
        metrics.end("A", token, MS, 0);
        metrics.frameEnd(5 * MS);
        metrics.frameStart(4 * MS, true, true, false);
        metrics.frameEnd(6 * MS);
        metrics.frameStart(6 * MS, true, true, false);
        metrics.frameEnd(7 * MS);
        Map<String, Object> snapshot = metrics.snapshot();
        assertEquals(2, number(snapshot, "timingAnomalies"));
        assertEquals(2, number(snapshot, "phaseMismatches"));
        assertNull(rows(snapshot, "frames").get(1).get("intervalMs"));
        assertEquals(1, number(map(snapshot.get("summary")), "intervalCount"));
        assertConserved(snapshot, 7);
    }

    @Test
    void stackIsBoundedAt64IncludingFrameAndRejectedTokenIsNoOp() {
        SessionMetrics metrics = new SessionMetrics(1);
        metrics.frameStart(0, true, true, false);
        long[] tokens = new long[63];
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = metrics.begin("A", i + 1);
            assertNotEquals(0, tokens[i]);
        }
        long rejected = metrics.begin("A", 64);
        assertEquals(0, rejected);
        metrics.end("A", rejected, 100, 0);
        for (int i = tokens.length - 1; i >= 0; i--) {
            metrics.end("A", tokens[i], 163 - i, 0);
        }
        metrics.frameEnd(200);
        Map<String, Object> snapshot = metrics.snapshot();
        assertEquals(1, number(snapshot, "depthOverflows"));
        assertEquals(0, number(snapshot, "phaseMismatches"));
        assertEquals(63, number(phase(snapshot, "A"), "calls"));
        assertConserved(snapshot, 200.0 / MS);
    }

    @Test
    void phaseNamesAreBoundedAt32IncludingFrameButExistingNamesRemainUsable() {
        SessionMetrics metrics = new SessionMetrics(1);
        metrics.frameStart(0, true, true, false);
        for (int i = 1; i < 32; i++) {
            String name = "P" + i;
            long token = metrics.begin(name, 2L * i);
            assertNotEquals(0, token);
            metrics.end(name, token, 2L * i + 1, 0);
        }
        assertEquals(0, metrics.begin("overflow", 100));
        long token = metrics.begin("P1", 102);
        assertNotEquals(0, token);
        metrics.end("P1", token, 103, 0);
        metrics.frameEnd(200);
        Map<String, Object> snapshot = metrics.snapshot();
        assertEquals(1, number(snapshot, "phaseOverflows"));
        assertEquals(32, rows(snapshot, "phases").size());
        assertEquals(2, number(phase(snapshot, "P1"), "calls"));
    }

    @Test
    void invalidNamesAndNegativeBytesAreCounted() {
        SessionMetrics metrics = new SessionMetrics(1);
        metrics.frameStart(0, true, true, false);
        assertEquals(0, metrics.begin(null, 0));
        assertEquals(0, metrics.begin(" ", 0));
        assertEquals(0, metrics.begin("FRAME", 0));
        long token = metrics.begin("A", MS);
        metrics.end("A", token, 2 * MS, -1);
        metrics.frameEnd(3 * MS);
        assertEquals(4, number(metrics.snapshot(), "phaseMismatches"));
        assertEquals(0, number(phase(metrics.snapshot(), "A"), "bytes"));
    }

    @Test
    void capacityNeverGrowsOrOverwritesAndInvalidCapacitiesFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new SessionMetrics(0));
        assertThrows(IllegalArgumentException.class, () -> new SessionMetrics(-1));
        SessionMetrics metrics = new SessionMetrics(1);
        assertFalse(metrics.isFull());
        frame(metrics, 0, 1, true, true, false);
        assertTrue(metrics.isFull());
        Map<String, Object> expected = metrics.snapshot();
        frame(metrics, 10, 3, false, false, true);
        assertEquals(0, metrics.begin("ignored", 20 * MS));
        assertEquals(1, metrics.frameCount());
        assertEquals(expected, metrics.snapshot());
    }

    @Test
    void repeatedFrameStartRecoversMissingEndWithoutExceedingCapacity() {
        SessionMetrics metrics = new SessionMetrics(1);
        metrics.frameStart(0, true, true, false);
        metrics.begin("A", MS);
        metrics.frameStart(5 * MS, false, false, true);
        assertTrue(metrics.isFull());
        assertEquals(1, metrics.frameCount());
        assertEquals(2, number(metrics.snapshot(), "phaseMismatches"));
        assertConserved(metrics.snapshot(), 5);
    }

    @Test
    void unfinishedFrameIsExcludedAndFlagged() {
        SessionMetrics metrics = new SessionMetrics(2);
        metrics.frameStart(0, true, true, false);
        long token = metrics.begin("A", MS);
        metrics.end("A", token, 2 * MS, 1);
        Map<String, Object> partial = metrics.snapshot();
        assertEquals(true, partial.get("incompleteFrame"));
        assertEquals(0, metrics.frameCount());
        assertTrue(rows(partial, "frames").isEmpty());
        assertTrue(rows(partial, "phases").isEmpty());
        metrics.frameEnd(3 * MS);
        assertEquals(false, metrics.snapshot().get("incompleteFrame"));
        assertEquals(1, number(phase(metrics.snapshot(), "A"), "calls"));
    }

    @Test
    void snapshotIsDeeplyDetachedAndCannotChangeStoredResults() {
        SessionMetrics metrics = new SessionMetrics(2);
        frame(metrics, 0, 1, true, true, false);
        Map<String, Object> expected = metrics.snapshot();
        Map<String, Object> detached = metrics.snapshot();
        map(detached.get("summary")).put("frameCount", 999);
        map(detached.get("foregroundWorld")).clear();
        rows(detached, "phases").getFirst().put("name", "changed");
        map(rows(detached, "frames").getFirst().get("phasesMs")).put("FRAME", 999.0);
        rows(detached, "frames").getFirst().put("workMs", 999.0);
        rows(detached, "frames").clear();
        detached.clear();
        assertEquals(expected, metrics.snapshot());
        frame(metrics, 10, 1, true, true, false);
        assertEquals(1, number(map(expected.get("summary")), "frameCount"));
    }

    private static void frame(SessionMetrics metrics, long startMs, long workMs,
                              boolean focused, boolean inWorld, boolean screenOpen) {
        metrics.frameStart(startMs * MS, focused, inWorld, screenOpen);
        metrics.frameEnd((startMs + workMs) * MS);
    }

    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static List<Map<String, Object>> rows(Map<String, Object> map, String key) {
        return (List<Map<String, Object>>) map.get(key);
    }

    private static long number(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).longValue();
    }

    private static double decimal(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).doubleValue();
    }

    private static Map<String, Object> phase(Map<String, Object> snapshot, String name) {
        return rows(snapshot, "phases").stream().filter(row -> name.equals(row.get("name"))).findFirst().orElseThrow();
    }

    private static void assertPhase(Map<String, Object> snapshot, String name, long calls,
                                    double total, double self, double max, long bytes) {
        Map<String, Object> phase = phase(snapshot, name);
        assertEquals(calls, number(phase, "calls"));
        assertEquals(total, decimal(phase, "totalMs"), 1e-10);
        assertEquals(self, decimal(phase, "selfMs"), 1e-10);
        assertEquals(max, decimal(phase, "maxMs"), 1e-10);
        assertEquals(bytes, number(phase, "bytes"));
    }

    private static void assertConserved(Map<String, Object> snapshot, double workMs) {
        double sum = 0;
        for (Map<String, Object> phase : rows(snapshot, "phases")) {
            double self = decimal(phase, "selfMs");
            assertTrue(self >= 0);
            sum += self;
        }
        assertEquals(workMs, sum, 1e-10);
    }
}

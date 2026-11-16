package io.wifi.sreperf.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SampleAccumulatorTest {
    private static StackTraceElement f(String owner, String method) { return new StackTraceElement(owner, method, "Test.java", 12); }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> data, String key) { return (List<Map<String, Object>>) data.get(key); }

    @Test void nearestAndInclusiveAreNotConfusedAndRecursionIsDeduplicated() {
        var a = new SampleAccumulator(8, 8);
        a.record("Render thread", Thread.State.RUNNABLE, new StackTraceElement[]{f("GL", "upload"), f("modA", "work"), f("modA", "work"), f("modB", "call")});
        var d = a.snapshot(f -> f.getClassName().startsWith("mod") ? "mod:" + f.getClassName() : "native");
        var mods = rows(d, "mods");
        var first = mods.stream().filter(r -> r.get("mod").equals("modA")).findFirst().orElseThrow();
        var second = mods.stream().filter(r -> r.get("mod").equals("modB")).findFirst().orElseThrow();
        assertEquals(1L, first.get("nearestPathSamples"));
        assertEquals(1L, first.get("inclusivePathSamples"));
        assertEquals(0L, second.get("nearestPathSamples"));
        assertEquals(1L, second.get("inclusivePathSamples"));
        assertEquals(100.0, first.get("shareOfThreadSamplesPercent"));
    }

    @Test void capacityDropsNewStacksButKeepsExistingCounts() {
        var a = new SampleAccumulator(1, 1);
        a.record("Render", Thread.State.RUNNABLE, new StackTraceElement[]{f("A", "a"), f("B", "b")});
        a.record("Render", Thread.State.RUNNABLE, new StackTraceElement[]{f("C", "c")});
        a.record("Render", Thread.State.RUNNABLE, new StackTraceElement[]{f("A", "a")});
        var d = a.snapshot(f -> "mod:A");
        assertEquals(3L, d.get("attemptedSamples"));
        assertEquals(2L, d.get("recordedSamples"));
        assertEquals(1L, d.get("droppedSamples"));
        assertEquals(1L, d.get("truncatedStacks"));
        assertEquals(1, d.get("uniqueStacks"));
        assertEquals(200.0 / 3.0, (Double) rows(d, "mods").getFirst().get("shareOfThreadSamplesPercent"), 0.00001);
    }

    @Test void waitingAndThreadDenominatorsRemainSeparate() {
        var a = new SampleAccumulator(8, 8);
        a.record("Render", Thread.State.WAITING, new StackTraceElement[]{f("A", "wait")});
        a.record("Sound", Thread.State.RUNNABLE, new StackTraceElement[]{f("A", "work")});
        var mods = rows(a.snapshot(f -> "mod:A"), "mods");
        var render = mods.stream().filter(r -> r.get("thread").equals("Render")).findFirst().orElseThrow();
        assertEquals(1L, render.get("nonRunnableSamples"));
        assertEquals(0L, render.get("runnableSamples"));
        assertEquals(100.0, render.get("shareOfThreadSamplesPercent"));
    }

    @Test void emptyAndStoppedSamplesDoNotInventData() {
        var a = new SampleAccumulator(1, 2);
        a.record("Render", Thread.State.RUNNABLE, new StackTraceElement[0]);
        a.stop();
        a.record("Render", Thread.State.RUNNABLE, new StackTraceElement[]{f("A", "work")});
        var d = a.snapshot(f -> "unknown");
        assertEquals(1L, d.get("attemptedSamples"));
        assertEquals(1L, d.get("droppedSamples"));
        assertTrue(rows(d, "mods").isEmpty());
        assertTrue(rows(d, "hotspots").isEmpty());
    }

    @Test void snapshotAndInputArraysAreDetached() {
        var a = new SampleAccumulator(2, 2);
        StackTraceElement[] frames = {f("A", "work")};
        a.record("Render", Thread.State.RUNNABLE, frames);
        frames[0] = f("Changed", "oops");
        var first = a.snapshot(f -> "mod:" + f.getClassName());
        rows(first, "mods").clear();
        var second = a.snapshot(f -> "mod:" + f.getClassName());
        assertEquals("A", rows(second, "mods").getFirst().get("mod"));
    }

    @Test void threadNamesAreBoundedToo() {
        var a = new SampleAccumulator(100, 1);
        for (int i = 0; i < 100; i++) a.record("Sound-" + i, Thread.State.RUNNABLE, new StackTraceElement[]{f("A", "work")});
        var d = a.snapshot(f -> "unknown");
        assertEquals(64, rows(d, "threads").size());
        assertEquals(36L, d.get("droppedSamples"));
    }

    @Test void unknownPathsRemainUnattributed() {
        var a = new SampleAccumulator(2, 2);
        a.record("Render", Thread.State.RUNNABLE, new StackTraceElement[]{f("Vanilla", "work")});
        var d = a.snapshot(f -> "unknown");
        assertEquals(1L, d.get("unattributedSamples"));
        assertTrue(rows(d, "mods").isEmpty());
    }

    @Test void invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SampleAccumulator(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SampleAccumulator(1, 0));
    }
}

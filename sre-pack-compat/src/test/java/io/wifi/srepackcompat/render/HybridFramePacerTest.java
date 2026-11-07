package io.wifi.srepackcompat.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HybridFramePacerTest {
    private static final class Time {
        long now;
        long clockStep = 10_000;
        final List<Long> parks = new ArrayList<>();
        long read() { long value = now; now += clockStep; return value; }
        void park(long nanos) { parks.add(nanos); now += nanos; }
        HybridFramePacer pacer() { return new HybridFramePacer(this::read, this::park); }
    }

    @Test void sleepsLongPartAndSpinsFinalMillisecond() {
        Time time = new Time();
        var pacer = time.pacer();
        pacer.pace(100);
        assertEquals(1, time.parks.size());
        assertTrue(time.parks.getFirst() > 8_900_000 && time.parks.getFirst() <= 9_000_000);
        assertTrue(time.now >= 10_000_000 && time.now < 10_100_000);
    }

    @Test void slowFrameDoesNotWaitOrAccumulateCatchupDebt() {
        Time time = new Time();
        var pacer = time.pacer();
        time.now = 1_000_000_000L;
        pacer.pace(100);
        assertTrue(time.parks.isEmpty());
        pacer.pace(100);
        assertEquals(1, time.parks.size());
        assertTrue(time.now >= 1_010_000_000L && time.now < 1_010_100_000L);
    }

    @Test void spuriousWakeupsAreRechecked() {
        Time time = new Time();
        var pacer = new HybridFramePacer(time::read, nanos -> {
            time.parks.add(nanos);
            time.now += nanos / 2;
        });
        pacer.pace(60);
        assertTrue(time.parks.size() > 1);
        assertTrue(time.now >= 1_000_000_000L / 60);
    }

    @Test void oversleepBecomesNextFramesActualBaseline() {
        Time time = new Time();
        var pacer = new HybridFramePacer(time::read, nanos -> {
            time.parks.add(nanos);
            time.now += nanos + 5_000_000L;
        });
        pacer.pace(100);
        long afterFirst = time.now;
        pacer.pace(100);
        assertEquals(2, time.parks.size());
        assertTrue(time.now - afterFirst >= 10_000_000L);
    }

    @Test void respectsChangedCap() {
        Time time = new Time();
        var pacer = time.pacer();
        pacer.pace(100);
        long first = time.now;
        pacer.pace(50);
        assertTrue(time.now - first >= 19_900_000L);
        assertTrue(time.now - first < 20_100_000L);
    }

    @Test void nonPositiveCapsDoNotDivideByZeroOrPark() {
        Time time = new Time();
        var pacer = time.pacer();
        pacer.pace(0);
        pacer.pace(-1);
        assertTrue(time.parks.isEmpty());
    }

    @Test void interruptedThreadDoesNotSpinOrClearInterrupt() {
        Time time = new Time();
        var pacer = time.pacer();
        Thread.currentThread().interrupt();
        try {
            pacer.pace(1);
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(time.parks.isEmpty());
        } finally { Thread.interrupted(); }
    }

    @Test void nanoTimeWrapAndVeryHighCapsRemainBounded() {
        Time time = new Time();
        time.now = Long.MAX_VALUE - 500_000;
        long start = time.now;
        var pacer = time.pacer();
        pacer.pace(100);
        assertTrue(time.now - start >= 10_000_000L);
        pacer.pace(Integer.MAX_VALUE);
        assertEquals(1, time.parks.size());
    }
}

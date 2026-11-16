package io.wifi.sreperf.memory;

import org.junit.jupiter.api.Test;

import java.lang.management.MemoryUsage;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JvmMemorySnapshotTest {
    @Test void preservesUnknownLimitsInsteadOfConvertingToZero() {
        Map<String, Object> usage = JvmMemorySnapshot.usage(new MemoryUsage(-1, 100, 200, -1));
        assertEquals(100L, usage.get("usedBytes"));
        assertEquals(200L, usage.get("committedBytes"));
        assertNull(usage.get("maxBytes"));
        assertNull(usage.get("initBytes"));
        assertNull(JvmMemorySnapshot.usage(null));
        assertNull(JvmMemorySnapshot.known(-1));
        assertEquals(0L, JvmMemorySnapshot.known(0));
    }

    @Test void rssUsesKilobytesAndRejectsOtherMetricsUnitsAndOverflow() {
        assertEquals(123L * 1024, JvmMemorySnapshot.parseLinuxRss("VmRSS:\t123 kB"));
        assertThrows(IllegalArgumentException.class, () -> JvmMemorySnapshot.parseLinuxRss("VmSize: 123 kB"));
        assertThrows(IllegalArgumentException.class, () -> JvmMemorySnapshot.parseLinuxRss("VmRSS: 123 MB"));
        assertThrows(IllegalArgumentException.class, () -> JvmMemorySnapshot.parseLinuxRss("VmRSS: -1 kB"));
        assertThrows(ArithmeticException.class, () -> JvmMemorySnapshot.parseLinuxRss("VmRSS: 9223372036854775807 kB"));
    }

    @Test void liveSnapshotIncludesSeparateJvmAndOsMeasurements() {
        Map<String, Object> out = JvmMemorySnapshot.capture();
        assertTrue(out.containsKey("heap"));
        assertTrue(out.containsKey("nonHeap"));
        assertTrue(out.containsKey("memoryPools"));
        assertTrue(out.containsKey("bufferPools"));
        assertTrue(out.containsKey("gcCumulativeSinceJvmStart"));
        assertTrue(out.containsKey("threads"));
        Map<?, ?> os = (Map<?, ?>) out.get("osAndProcess");
        Map<?, ?> rss = (Map<?, ?>) os.get("residentSetBytes");
        assertTrue(rss.containsKey("value"));
        assertTrue(rss.get("source").toString().contains("VmRSS"));
        if (!System.getProperty("os.name").toLowerCase().contains("linux")) {
            assertNull(rss.get("value"));
            assertTrue(rss.containsKey("unavailable"));
        }
        assertTrue(out.get("measurement").toString().contains("not atomic"));
    }
}

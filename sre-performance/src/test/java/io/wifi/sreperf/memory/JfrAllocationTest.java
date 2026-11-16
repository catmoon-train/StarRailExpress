package io.wifi.sreperf.memory;

import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JfrAllocationTest {
    @TempDir Path temp;
    @Name("sreperf.TestOnlyEvent") static class UnrelatedEvent extends Event { }

    @Test void readerIgnoresUnrelatedEventsAndKeepsZeroSamplesExplicit() throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        Path file = temp.resolve("unrelated.jfr");
        try (Recording recording = new Recording()) {
            recording.enable(UnrelatedEvent.class);
            recording.start();
            new UnrelatedEvent().commit();
            recording.stop();
            recording.dump(file);
        }
        var out = JfrAllocation.summarize(file, frame -> fail("No allocation frames expected"));
        assertEquals(0L, out.get("samples"));
        assertEquals(List.of(), out.get("classes"));
    }

    @Test void explicitBoundedRecordingStopsAndCanBeReadBackWithoutAHeapDump() throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        int before = FlightRecorder.getFlightRecorder().getRecordings().size();
        Path file = temp.resolve("allocation.jfr");
        var out = JfrAllocation.record(file, 10);
        assertEquals(true, out.get("supported"));
        assertEquals(10, out.get("requestedSeconds"));
        assertEquals(JfrAllocation.MAX_BYTES, out.get("maxRecordingBytes"));
        assertTrue(Files.size(file) > 0);
        assertTrue(out.containsKey("classes"));
        assertTrue(out.containsKey("stacks"));
        assertEquals(before, FlightRecorder.getFlightRecorder().getRecordings().size());
    }

    @Test void invalidDurationsRejectedBeforeRecording() {
        assertThrows(IllegalArgumentException.class, () -> JfrAllocation.record(temp.resolve("none.jfr"), 9));
        assertThrows(IllegalArgumentException.class, () -> JfrAllocation.record(temp.resolve("none.jfr"), 181));
        assertFalse(Files.exists(temp.resolve("none.jfr")));
    }
}

package io.wifi.sreperf.memory;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MemoryDiagnosticsTest {
    @TempDir Path temp;

    @Test void snapshotReadsWorldOnCallerAndExportsWithoutStartingHistogramOrJfr() throws Exception {
        Thread caller = Thread.currentThread();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Path> saved = new AtomicReference<>();
        AtomicReference<Thread> worker = new AtomicReference<>();
        List<String> messages = new CopyOnWriteArrayList<>();
        assertEquals(1, MemoryDiagnostics.request(temp, "client", MemoryDiagnostics.Kind.SNAPSHOT, 0, () -> {
            assertSame(caller, Thread.currentThread());
            return Map.of("scope", "local test", "worlds", List.of());
        }, (key, args) -> {
            messages.add(key);
            if (key.equals("sreperf.memory.saved")) {
                saved.set(Path.of(args[0].toString()));
                worker.set(Thread.currentThread());
                done.countDown();
            }
        }));
        assertTrue(done.await(10, TimeUnit.SECONDS), messages.toString());
        worker.get().join(5000);
        assertNotSame(caller, worker.get());
        var report = JsonParser.parseString(Files.readString(saved.get().resolve("report.json"))).getAsJsonObject();
        assertEquals("client", report.get("side").getAsString());
        assertTrue(report.has("jvm"));
        assertFalse(report.has("histogram"));
        assertFalse(report.has("allocation"));
        assertFalse(Files.exists(saved.get().resolve("histogram.txt")));
        assertFalse(Files.exists(saved.get().resolve("allocation.jfr")));
    }

    @Test void busyGateDoesNotReadSecondWorldAndDoesNotLeaveIdleThreads() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        Thread first = new Thread(() -> MemoryDiagnostics.request(temp, "server", MemoryDiagnostics.Kind.SNAPSHOT, 0, () -> {
            entered.countDown();
            try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new RuntimeException(e); }
            return Map.of();
        }, (key, args) -> {
            if (key.equals("sreperf.memory.saved") || key.equals("sreperf.memory.failed")) {
                worker.set(Thread.currentThread()); done.countDown();
            }
        }));
        first.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            AtomicBoolean read = new AtomicBoolean();
            assertEquals(0, MemoryDiagnostics.request(temp, "client", MemoryDiagnostics.Kind.SNAPSHOT, 0,
                    () -> { read.set(true); return Map.of(); }, (key, args) -> assertEquals("sreperf.memory.busy", key)));
            assertFalse(read.get());
        } finally { release.countDown(); }
        first.join(5000);
        assertTrue(done.await(10, TimeUnit.SECONDS));
        worker.get().join(5000);
        assertFalse(worker.get().isAlive());
    }

    @Test void worldFailureAndInvalidDurationDoNotStartWorker() {
        assertEquals(0, MemoryDiagnostics.request(temp, "client", MemoryDiagnostics.Kind.SNAPSHOT, 0,
                () -> { throw new IllegalStateException("world unavailable"); },
                (key, args) -> assertEquals("sreperf.memory.failed", key)));
        for (int seconds : new int[]{0, 9, 181}) {
            assertThrows(IllegalArgumentException.class, () -> MemoryDiagnostics.request(temp, "client", MemoryDiagnostics.Kind.ALLOCATION,
                    seconds, () -> fail("Must validate before reading world"), (key, args) -> fail("Must validate first")));
        }
    }
}

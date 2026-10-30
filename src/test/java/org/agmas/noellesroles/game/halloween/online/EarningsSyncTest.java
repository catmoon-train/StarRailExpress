package org.agmas.noellesroles.game.halloween.online;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class EarningsSyncTest {
    @TempDir Path temp;
    final AtomicLong clock = new AtomicLong(1_000_000);
    final OnlineConfig config = new OnlineConfig();
    final List<String> warnings = new ArrayList<>();

    Earning earning() { return new Earning(UUID.randomUUID(), UUID.randomUUID(), 7, clock.get()); }
    EarningsSender sender(EarningsStore store, EarningsSink sink) {
        return new EarningsSender(store, sink, config, clock::get, () -> 0L, warnings::add);
    }
    static Set<UUID> ack(List<Earning> entries) {
        Set<UUID> ids = new HashSet<>();
        for (Earning entry : entries) ids.add(entry.id());
        return ids;
    }
    static Set<UUID> ackIds(UUID... ids) { return new HashSet<>(List.of(ids)); }

    @Test void waitsThirtySecondsAndDoesNotWritePerEarning() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (server, entries) -> { calls.incrementAndGet(); return ack(entries); });
            for (int i = 0; i < 127; i++) sender.record(earning());
            assertEquals(0, calls.get());
            assertFalse(sender.tick());
            clock.addAndGet(29_999); assertFalse(sender.tick());
            clock.incrementAndGet(); assertTrue(sender.tick());
            assertEquals(1, calls.get()); assertEquals(0, store.pendingCount());
        }
    }

    @Test void writesAt128AndNeverExceeds128() throws Exception {
        List<Integer> sizes = new ArrayList<>();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (server, entries) -> { sizes.add(entries.size()); return ack(entries); });
            for (int i = 0; i < 257; i++) sender.record(earning());
            assertTrue(sender.tick()); assertTrue(sender.tick()); assertFalse(sender.tick());
            assertEquals(List.of(128, 128), sizes);
            clock.addAndGet(30_000); assertTrue(sender.tick());
            assertEquals(List.of(128, 128, 1), sizes);
        }
    }

    @Test void unknownDatabaseCommitOutcomeRetriesIdenticalIdsTimesAndServer() throws Exception {
        List<List<Earning>> batches = new ArrayList<>();
        List<UUID> servers = new ArrayList<>();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (server, entries) -> {
                batches.add(List.copyOf(entries)); servers.add(server);
                if (batches.size() == 1) throw new IOException("Connection lost after commit");
                return ack(entries);
            });
            sender.record(earning()); sender.requestFlush(); assertFalse(sender.tick());
            assertEquals(1, store.pendingCount());
            clock.addAndGet(999); assertFalse(sender.tick()); assertEquals(1, batches.size());
            clock.incrementAndGet(); assertTrue(sender.tick());
            assertEquals(batches.get(0), batches.get(1)); assertEquals(servers.get(0), servers.get(1));
            assertEquals(0, store.pendingCount());
        }
    }

    @Test void partialDuplicateAndUnknownAcknowledgementsCannotDropUnconfirmed() throws Exception {
        Earning a = earning(), b = earning();
        AtomicInteger calls = new AtomicInteger();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (server, entries) -> calls.getAndIncrement() == 0
                    ? ackIds(a.id(), a.id(), UUID.randomUUID()) : ackIds(a.id(), b.id(), b.id()));
            sender.record(a); sender.record(b); sender.requestFlush(); assertFalse(sender.tick());
            assertFalse(store.isPending(a.id())); assertTrue(store.isPending(b.id()));
            clock.addAndGet(1000); assertTrue(sender.tick()); assertEquals(0, store.pendingCount());
            store.record(a); assertEquals(0, store.pendingCount());
        }
        try (EarningsStore recovered = new EarningsStore(temp)) {
            recovered.record(a); assertEquals(0, recovered.pendingCount());
        }
    }

    @Test void databaseErrorsNullAndEmptyAcknowledgementsRetainQueue() throws Exception {
        try (EarningsStore store = new EarningsStore(temp)) {
            Earning entry = earning(); store.record(entry);
            for (EarningsSink failure : List.<EarningsSink>of(
                    (server, entries) -> { throw new IOException("SQL failed"); },
                    (server, entries) -> null, (server, entries) -> Set.of())) {
                EarningsSender sender = sender(store, failure);
                sender.requestFlush(); assertFalse(sender.tick()); assertEquals(List.of(entry), store.pending(128));
            }
        }
    }

    @Test void ackForceFailureKeepsRecordAndRetriesSameIds() throws Exception {
        AtomicBoolean failAck = new AtomicBoolean(true);
        try (EarningsStore store = new EarningsStore(temp, payload -> {
            if (payload.startsWith("A ") && failAck.getAndSet(false)) throw new IOException("Simulated fsync failure");
        })) {
            EarningsSender sender = sender(store, (server, entries) -> ack(entries));
            Earning entry = earning(); sender.record(entry); sender.requestFlush();
            assertFalse(sender.tick()); assertTrue(store.isPending(entry.id()));
            clock.addAndGet(1000); assertTrue(sender.tick()); assertEquals(0, store.pendingCount());
        }
        try (EarningsStore store = new EarningsStore(temp)) { assertEquals(0, store.pendingCount()); }
    }

    @Test void failedNewRecordCanRetryStableReceiptWithoutIncompleteWalLine() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        Earning entry = earning();
        try (EarningsStore store = new EarningsStore(temp, payload -> {
            if (payload.startsWith("E ") && fail.getAndSet(false)) throw new IOException("Disk failure");
        })) {
            assertThrows(IOException.class, () -> store.record(entry)); assertEquals(0, store.pendingCount());
            store.record(entry); store.record(entry); assertEquals(List.of(entry), store.pending(128));
        }
        try (EarningsStore store = new EarningsStore(temp)) { assertEquals(List.of(entry), store.pending(128)); }
    }

    @Test void processCrashRecoversForcedRecordsAndStableServerId() throws Exception {
        Path arguments = temp.resolve("crash-writer.args");
        String classpath = System.getProperty("java.class.path").replace("\\", "\\\\").replace("\"", "\\\"");
        String directory = temp.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        Files.writeString(arguments, "-cp\n\"" + classpath + "\"\n" + CrashWriter.class.getName()
                + "\n\"" + directory + "\"\n", StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "@" + arguments).inheritIO().start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
        UUID serverId; Earning entry;
        try (EarningsStore recovered = new EarningsStore(temp)) {
            assertEquals(1, recovered.pendingCount()); serverId = recovered.serverId(); entry = recovered.pending(128).getFirst();
        }
        try (EarningsStore recovered = new EarningsStore(temp)) {
            assertEquals(serverId, recovered.serverId()); assertEquals(List.of(entry), recovered.pending(128));
        }
    }

    public static class CrashWriter {
        public static void main(String[] args) throws Exception {
            EarningsStore store = new EarningsStore(Path.of(args[0]));
            store.record(new Earning(UUID.randomUUID(), UUID.randomUUID(), 5, 123456));
            Runtime.getRuntime().halt(0);
        }
    }

    @Test void legacyHttpWalKeepsServerIdsEarnedTimesAndConfirmedHistory() throws Exception {
        UUID server = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID player = UUID.fromString("44444444-4444-4444-8444-444444444444");
        Earning confirmed = new Earning(UUID.fromString("22222222-2222-4222-8222-222222222222"), player, 2, 1792368000000L);
        Earning pending = new Earning(UUID.fromString("33333333-3333-4333-8333-333333333333"), player, 7, 1792368000001L);
        List<String> payloads = List.of("S " + server,
                "E " + confirmed.id() + " " + player + " 2 " + confirmed.earnedAt(),
                "A " + confirmed.id(), "E " + pending.id() + " " + player + " 7 " + pending.earnedAt());
        StringBuilder wal = new StringBuilder();
        for (String payload : payloads) wal.append(payload).append('\t').append(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)))).append('\n');
        Files.writeString(temp.resolve("earnings.wal"), wal, StandardCharsets.UTF_8);
        try (EarningsStore store = new EarningsStore(temp)) {
            assertEquals(server, store.serverId()); assertEquals(List.of(pending), store.pending(128));
            store.record(confirmed); assertEquals(1, store.pendingCount());
            EarningsSender sender = sender(store, (source, entries) -> {
                assertEquals(server, source); assertEquals(List.of(pending), entries); return ack(entries);
            });
            sender.requestFlush(); assertTrue(sender.tick()); assertEquals(0, store.pendingCount());
        }
    }

    @Test void incompleteTailPreservesAllCompleteRecordsAndArchivesTail() throws Exception {
        Earning entry = earning();
        try (EarningsStore store = new EarningsStore(temp)) { store.record(entry); }
        Files.writeString(temp.resolve("earnings.wal"), "E half-written-tail", StandardOpenOption.APPEND);
        try (EarningsStore store = new EarningsStore(temp)) {
            assertEquals(List.of(entry), store.pending(128)); store.record(earning());
        }
        try (var files = Files.list(temp)) {
            Path tail = files.filter(path -> path.getFileName().toString().startsWith("incomplete-tail-")).findFirst().orElseThrow();
            assertEquals("E half-written-tail", Files.readString(tail));
        }
        try (EarningsStore recovered = new EarningsStore(temp)) { assertEquals(2, recovered.pendingCount()); }
    }

    @Test void corruptCompleteMiddleOrLastLineFailsClosedWithoutChangingWal() throws Exception {
        try (EarningsStore store = new EarningsStore(temp)) { store.record(earning()); store.record(earning()); }
        Path wal = temp.resolve("earnings.wal"); byte[] contents = Files.readAllBytes(wal);
        int firstNewline = new String(contents, StandardCharsets.UTF_8).indexOf('\n'); contents[firstNewline + 1] = 'X';
        Files.write(wal, contents); assertThrows(IOException.class, () -> new EarningsStore(temp));
        assertArrayEquals(contents, Files.readAllBytes(wal));
    }

    @Test void oldAckCannotDeleteConcurrentNewIncomeAndDatabaseDoesNotBlockRecord() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor(); Earning a = earning(), b = earning();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (server, entries) -> {
                entered.countDown(); if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("Test timed out");
                return ackIds(a.id(), b.id());
            });
            sender.record(a); sender.requestFlush(); var future = executor.submit(sender::tick);
            assertTrue(entered.await(3, TimeUnit.SECONDS)); sender.record(b); release.countDown();
            assertTrue(future.get(3, TimeUnit.SECONDS)); assertEquals(List.of(b), store.pending(128));
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void stopStartRetainsPendingAndDoesNotRequireOnlinePlayers() throws Exception {
        Earning entry = earning(); EarningsStore store = new EarningsStore(temp); UUID serverId = store.serverId();
        EarningsSender offline = sender(store, (server, entries) -> { throw new IOException("Database offline"); });
        offline.record(entry); offline.requestFlush(); assertFalse(offline.tick()); offline.close();
        EarningsStore recovered = new EarningsStore(temp);
        try (EarningsSender online = sender(recovered, (server, entries) -> ack(entries))) {
            assertEquals(serverId, recovered.serverId()); assertEquals(List.of(entry), recovered.pending(128));
            online.requestFlush(); assertTrue(online.tick());
        }
    }

    @Test void shutdownHasBoundedDatabaseWaitAndRetainsDisk() throws Exception {
        CountDownLatch entered = new CountDownLatch(1); EarningsStore store = new EarningsStore(temp);
        EarningsSender sender = sender(store, (server, entries) -> {
            entered.countDown(); new CountDownLatch(1).await(); return ack(entries);
        });
        sender.record(earning()); sender.requestFlush(); sender.start(); assertTrue(entered.await(3, TimeUnit.SECONDS));
        long start = System.nanoTime(); sender.close(); assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5));
        try (EarningsStore recovered = new EarningsStore(temp)) { assertEquals(1, recovered.pendingCount()); }
    }

    @Test void exponentialBackoffCapsAtFiveMinutesAndWarningsAreThrottled() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (server, entries) -> { calls.incrementAndGet(); throw new IOException("private db password"); });
            sender.record(earning()); sender.requestFlush();
            for (int i = 0; i < 12; i++) {
                assertFalse(sender.tick()); assertEquals(i + 1, calls.get());
                long delay = Math.min(300_000L, 1000L << Math.min(i, 9)); clock.addAndGet(delay - 1); sender.requestFlush();
                assertFalse(sender.tick()); assertEquals(i + 1, calls.get()); clock.incrementAndGet();
            }
            assertTrue(warnings.size() < calls.get()); assertTrue(warnings.stream().noneMatch(text -> text.contains("private db password")));
            assertEquals(1, store.pendingCount());
        }
    }

    @Test void disconnectOnlyExpeditesExistingBatchAndIdleHintDoesNotWriteNextCoinEarly() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (server, entries) -> { calls.incrementAndGet(); return ack(entries); });
            sender.requestFlush(); sender.record(earning()); assertFalse(sender.tick());
            sender.requestFlush(); assertTrue(sender.tick()); assertEquals(1, calls.get());
        }
    }
}

package org.agmas.noellesroles.game.halloween.online;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
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

    EarningsSyncTest() { config.bridgeSecret = "0123456789abcdef0123456789abcdef"; }

    Earning earning() { return new Earning(UUID.randomUUID(), UUID.randomUUID(), 7, clock.get()); }
    EarningsSender sender(EarningsStore store, BridgeTransport transport) {
        return new EarningsSender(store, transport, config, clock::get, () -> 0L, warnings::add);
    }
    static BridgeTransport.Reply ack(byte[] body) {
        JsonArray ids = new JsonArray();
        for (var row : JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("entries"))
            ids.add(row.getAsJsonObject().get("id"));
        JsonObject reply = new JsonObject();
        reply.addProperty("ok", true);
        reply.add("acknowledgedIds", ids);
        return new BridgeTransport.Reply(200, reply.toString());
    }
    static BridgeTransport.Reply ackIds(UUID... ids) {
        JsonArray array = new JsonArray();
        for (UUID id : ids) array.add(id.toString());
        return new BridgeTransport.Reply(200, "{\"ok\":true,\"acknowledgedIds\":" + array + "}");
    }

    @Test void waitsThirtySecondsAndDoesNotSendPerEarning() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (body, time, sig) -> { calls.incrementAndGet(); return ack(body); });
            for (int i = 0; i < 127; i++) sender.record(earning());
            assertEquals(0, calls.get());
            assertFalse(sender.tick());
            clock.addAndGet(29_999);
            assertFalse(sender.tick());
            clock.incrementAndGet();
            assertTrue(sender.tick());
            assertEquals(1, calls.get());
            assertEquals(0, store.pendingCount());
        }
    }

    @Test void sendsAt128AndNeverExceeds128() throws Exception {
        List<Integer> sizes = new ArrayList<>();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (body, time, sig) -> {
                sizes.add(JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("entries").size());
                return ack(body);
            });
            for (int i = 0; i < 257; i++) sender.record(earning());
            assertTrue(sender.tick());
            assertTrue(sender.tick());
            assertFalse(sender.tick());
            assertEquals(List.of(128, 128), sizes);
            clock.addAndGet(30_000);
            assertTrue(sender.tick());
            assertEquals(List.of(128, 128, 1), sizes);
        }
    }

    @Test void responseLostRetriesIdenticalBodyButFreshTimestampAndSignature() throws Exception {
        List<byte[]> bodies = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        List<String> signatures = new ArrayList<>();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (body, time, sig) -> {
                bodies.add(body.clone()); times.add(time); signatures.add(sig);
                if (bodies.size() == 1) throw new IOException("Response lost after remote commit");
                return ack(body);
            });
            sender.record(earning());
            sender.requestFlush();
            assertFalse(sender.tick());
            assertEquals(1, store.pendingCount());
            clock.addAndGet(999);
            assertFalse(sender.tick());
            assertEquals(1, bodies.size());
            clock.incrementAndGet();
            assertTrue(sender.tick());
            assertArrayEquals(bodies.get(0), bodies.get(1));
            assertNotEquals(times.get(0), times.get(1));
            assertNotEquals(signatures.get(0), signatures.get(1));
            assertEquals(0, store.pendingCount());
        }
    }

    @Test void partialDuplicateAndUnknownAcknowledgementsCannotDropUnconfirmed() throws Exception {
        Earning a = earning(), b = earning();
        AtomicInteger calls = new AtomicInteger();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (body, time, sig) -> calls.getAndIncrement() == 0
                    ? ackIds(a.id(), a.id(), UUID.randomUUID()) : ackIds(a.id(), b.id(), b.id()));
            sender.record(a); sender.record(b); sender.requestFlush();
            assertFalse(sender.tick());
            assertFalse(store.isPending(a.id()));
            assertTrue(store.isPending(b.id()));
            clock.addAndGet(1000);
            assertTrue(sender.tick());
            assertEquals(0, store.pendingCount());
            store.record(a); // 拆栈携带的旧凭据在确认后仍不能重新发行。
            assertEquals(0, store.pendingCount());
        }
        try (EarningsStore recovered = new EarningsStore(temp)) {
            recovered.record(a);
            assertEquals(0, recovered.pendingCount());
        }
    }

    @Test void allFailureStatusesAndMalformedAcknowledgementsRetainQueue() throws Exception {
        for (int status : new int[]{401, 429, 500, 503, 201, 302})
            assertThrows(IOException.class, () -> BridgeProtocol.acknowledgements(status, "{\"ok\":true,\"acknowledgedIds\":[]}"));
        List<String> bad = List.of("{}", "not json", "{\"ok\":false,\"acknowledgedIds\":[]}",
                "{\"ok\":\"true\",\"acknowledgedIds\":[]}", "{\"ok\":true,\"acknowledgedIds\":[12]}",
                "{\"ok\":true,\"acknowledgedIds\":[\"invalid\"]}");
        try (EarningsStore store = new EarningsStore(temp)) {
            for (String response : bad) {
                EarningsSender sender = sender(store, (body, time, sig) -> new BridgeTransport.Reply(200, response));
                if (store.pendingCount() == 0) sender.record(earning());
                sender.requestFlush(); assertFalse(sender.tick()); assertEquals(1, store.pendingCount());
            }
        }
    }

    @Test void ackForceFailureKeepsRecordAndRetriesSameIds() throws Exception {
        AtomicBoolean failAck = new AtomicBoolean(true);
        try (EarningsStore store = new EarningsStore(temp, payload -> {
            if (payload.startsWith("A ") && failAck.getAndSet(false)) throw new IOException("Simulated fsync failure");
        })) {
            EarningsSender sender = sender(store, (body, time, sig) -> ack(body));
            Earning entry = earning(); sender.record(entry); sender.requestFlush();
            assertFalse(sender.tick());
            assertTrue(store.isPending(entry.id()));
            clock.addAndGet(1000);
            assertTrue(sender.tick());
            assertEquals(0, store.pendingCount());
        }
        try (EarningsStore store = new EarningsStore(temp)) { assertEquals(0, store.pendingCount()); }
    }

    @Test void failedNewRecordCanRetryStableReceiptWithoutIncompleteWalLine() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        Earning entry = earning();
        try (EarningsStore store = new EarningsStore(temp, payload -> {
            if (payload.startsWith("E ") && fail.getAndSet(false)) throw new IOException("Disk failure");
        })) {
            assertThrows(IOException.class, () -> store.record(entry));
            assertEquals(0, store.pendingCount());
            store.record(entry); store.record(entry);
            assertEquals(List.of(entry), store.pending(128));
        }
        try (EarningsStore store = new EarningsStore(temp)) { assertEquals(List.of(entry), store.pending(128)); }
    }

    @Test void processCrashRecoversForcedRecordsAndStableServerId() throws Exception {
        // Gradle 的完整 classpath 在 Windows 超过 CreateProcess 命令行限制，交给 JVM 参数文件。
        Path arguments = temp.resolve("crash-writer.args");
        String classpath = System.getProperty("java.class.path").replace("\\", "\\\\").replace("\"", "\\\"");
        String directory = temp.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        Files.writeString(arguments, "-cp\n\"" + classpath + "\"\n" + CrashWriter.class.getName()
                + "\n\"" + directory + "\"\n", StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "@" + arguments).inheritIO().start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        UUID serverId;
        Earning entry;
        try (EarningsStore recovered = new EarningsStore(temp)) {
            assertEquals(1, recovered.pendingCount());
            serverId = recovered.serverId(); entry = recovered.pending(128).getFirst();
        }
        try (EarningsStore recovered = new EarningsStore(temp)) {
            assertEquals(serverId, recovered.serverId());
            assertEquals(List.of(entry), recovered.pending(128));
        }
    }

    public static class CrashWriter {
        public static void main(String[] args) throws Exception {
            EarningsStore store = new EarningsStore(Path.of(args[0]));
            store.record(new Earning(UUID.randomUUID(), UUID.randomUUID(), 5, 123456));
            Runtime.getRuntime().halt(0); // 不 close、不 shutdown hook，模拟 JVM 突然退出。
        }
    }

    @Test void incompleteTailPreservesAllCompleteRecordsAndArchivesTail() throws Exception {
        Earning entry = earning();
        try (EarningsStore store = new EarningsStore(temp)) { store.record(entry); }
        Files.writeString(temp.resolve("earnings.wal"), "E half-written-tail", StandardOpenOption.APPEND);
        try (EarningsStore recovered = new EarningsStore(temp)) {
            assertEquals(List.of(entry), recovered.pending(128));
            recovered.record(earning());
        }
        try (var files = Files.list(temp)) {
            Path tail = files.filter(path -> path.getFileName().toString().startsWith("incomplete-tail-")).findFirst().orElseThrow();
            assertEquals("E half-written-tail", Files.readString(tail));
        }
        try (EarningsStore recovered = new EarningsStore(temp)) { assertEquals(2, recovered.pendingCount()); }
    }

    @Test void corruptCompleteMiddleOrLastLineFailsClosedWithoutChangingWal() throws Exception {
        try (EarningsStore store = new EarningsStore(temp)) { store.record(earning()); store.record(earning()); }
        Path wal = temp.resolve("earnings.wal");
        byte[] contents = Files.readAllBytes(wal);
        int firstNewline = new String(contents, StandardCharsets.UTF_8).indexOf('\n');
        contents[firstNewline + 1] = 'X';
        Files.write(wal, contents);
        assertThrows(IOException.class, () -> new EarningsStore(temp));
        assertArrayEquals(contents, Files.readAllBytes(wal));
    }

    @Test void oldAckCannotDeleteConcurrentNewIncomeAndHttpDoesNotBlockRecord() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        Earning a = earning(), b = earning();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (body, time, sig) -> {
                entered.countDown();
                if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("Test timed out");
                return ackIds(a.id(), b.id());
            });
            sender.record(a); sender.requestFlush();
            var future = executor.submit(sender::tick);
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            sender.record(b); // 若 record 与 HTTP 共用锁，这里会阻塞并导致测试失败。
            release.countDown();
            assertTrue(future.get(3, TimeUnit.SECONDS));
            assertEquals(List.of(b), store.pending(128));
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void stopStartRetainsPendingAndDoesNotRequireOnlinePlayers() throws Exception {
        Earning entry = earning();
        UUID serverId;
        EarningsStore store = new EarningsStore(temp);
        serverId = store.serverId();
        EarningsSender offline = sender(store, (body, time, sig) -> { throw new IOException("Offline"); });
        offline.record(entry); offline.requestFlush(); assertFalse(offline.tick()); offline.close();
        EarningsStore recovered = new EarningsStore(temp);
        try (EarningsSender online = sender(recovered, (body, time, sig) -> ack(body))) {
            assertEquals(serverId, recovered.serverId());
            assertEquals(List.of(entry), recovered.pending(128));
            online.requestFlush(); assertTrue(online.tick());
        }
    }

    @Test void shutdownHasBoundedNetworkWaitAndRetainsDisk() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        EarningsStore store = new EarningsStore(temp);
        EarningsSender sender = sender(store, (body, time, sig) -> {
            entered.countDown(); new CountDownLatch(1).await(); return ack(body);
        });
        sender.record(earning()); sender.requestFlush(); sender.start();
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        long start = System.nanoTime(); sender.close();
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5));
        try (EarningsStore recovered = new EarningsStore(temp)) { assertEquals(1, recovered.pendingCount()); }
    }

    @Test void exponentialBackoffCapsAtFiveMinutesAndWarningsAreThrottled() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (body, time, sig) -> { calls.incrementAndGet(); return new BridgeTransport.Reply(429, "private response"); });
            sender.record(earning()); sender.requestFlush();
            for (int i = 0; i < 12; i++) {
                assertFalse(sender.tick());
                assertEquals(i + 1, calls.get());
                long delay = Math.min(300_000L, 1000L << Math.min(i, 9));
                clock.addAndGet(delay - 1); sender.requestFlush();
                assertFalse(sender.tick()); assertEquals(i + 1, calls.get());
                clock.incrementAndGet();
            }
            assertTrue(warnings.size() < calls.get());
            assertTrue(warnings.stream().noneMatch(text -> text.contains("private response") || text.contains(config.bridgeSecret)));
            assertEquals(1, store.pendingCount());
        }
    }

    @Test void disconnectOnlyExpeditesExistingBatchAndIdleHintDoesNotSendNextCoinEarly() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = sender(store, (body, time, sig) -> { calls.incrementAndGet(); return ack(body); });
            sender.requestFlush(); sender.record(earning()); assertFalse(sender.tick());
            sender.requestFlush(); assertTrue(sender.tick()); assertEquals(1, calls.get());
        }
    }
}

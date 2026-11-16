package net.exmo.sre.sync;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MysqlPlayerDataStoreShutdownTest {
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch closed = new CountDownLatch(1);
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicReference<Thread> sqlThread = new AtomicReference<>();
    private Object originalSource;
    private boolean installed;

    private void install(HikariDataSource source) throws Exception {
        Field field = sourceField();
        originalSource = field.get(null);
        field.set(null, source);
        installed = true;
    }

    private HikariDataSource blockedDatabase() {
        return new HikariDataSource() {
            @Override public Connection getConnection() throws SQLException {
                sqlThread.set(Thread.currentThread());
                attempts.incrementAndGet();
                entered.countDown();
                awaitRelease();
                throw new SQLException("injected write failure", "42000");
            }
            @Override public void close() { closed.countDown(); }
        };
    }

    private void awaitRelease() {
        try {
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("release timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    @AfterEach
    void cleanUp() throws Exception {
        release.countDown();
        assertTrue(MysqlPlayerDataStore.awaitPendingWrites(5000));
        if (installed) sourceField().set(null, originalSource);
        Thread.interrupted();
    }

    @Test
    void batchSubmissionNeverRunsJdbcOnCallerAndWaitUsesOneDeadline() throws Exception {
        install(blockedDatabase());
        Thread caller = Thread.currentThread();
        CompletableFuture<Boolean> first = assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> MysqlPlayerDataStore.saveBatchAsync(UUID.randomUUID(), Map.of("stats", "{}"), 1));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        assertNotSame(caller, sqlThread.get());
        for (int i = 0; i < 20; i++) {
            MysqlPlayerDataStore.saveBatchAsync(UUID.randomUUID(), Map.of("stats", "{}"), i + 2);
        }
        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> assertFalse(MysqlPlayerDataStore.awaitPendingWrites(80)));
        assertFalse(first.isDone());
        release.countDown();
        assertTrue(MysqlPlayerDataStore.awaitPendingWrites(5000));
        assertFalse(first.get());
        assertEquals(21, attempts.get());
    }

    @Test
    void finalSnapshotWaitsForEarlierWriteForSamePlayer() throws Exception {
        install(blockedDatabase());
        UUID uuid = UUID.randomUUID();
        CompletableFuture<Boolean> first = MysqlPlayerDataStore.saveBatchAsync(uuid, Map.of("stats", "old"), 1);
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        CompletableFuture<Boolean> last = MysqlPlayerDataStore.saveBatchAsync(uuid, Map.of("stats", "last"), 2);
        assertFalse(last.isDone());
        assertEquals(1, attempts.get());
        release.countDown();
        first.get(2, TimeUnit.SECONDS);
        last.get(2, TimeUnit.SECONDS);
        assertEquals(2, attempts.get());
    }

    @Test
    void shutdownDrainsSubmittedWritesBeforeClosingPool() throws Exception {
        install(blockedDatabase());
        MysqlPlayerDataStore.saveBatchAsync(UUID.randomUUID(), Map.of("stats", "{}"), 1);
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        CompletableFuture<Void> shutdown = CompletableFuture.runAsync(MysqlPlayerDataStore::shutdown);
        assertFalse(closed.await(100, TimeUnit.MILLISECONDS));
        release.countDown();
        shutdown.get(3, TimeUnit.SECONDS);
        assertEquals(0, closed.getCount());
        assertFalse(MysqlPlayerDataStore.isAvailable());
    }

    @Test
    void stuckPoolCloseCannotHoldShutdownIndefinitely() throws Exception {
        AtomicReference<Thread> closeThread = new AtomicReference<>();
        install(new HikariDataSource() {
            @Override public void close() {
                closeThread.set(Thread.currentThread());
                entered.countDown();
                awaitRelease();
                closed.countDown();
            }
        });
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(6), MysqlPlayerDataStore::shutdown);
            assertEquals(0, entered.getCount());
            assertEquals(1, closed.getCount());
            assertTrue(closeThread.get().isDaemon());
            assertFalse(MysqlPlayerDataStore.isAvailable());
        } finally {
            release.countDown();
            assertTrue(closed.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void interruptedWaitRestoresInterruptFlag() throws Exception {
        install(blockedDatabase());
        MysqlPlayerDataStore.saveBatchAsync(UUID.randomUUID(), Map.of("stats", "{}"), 1);
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        try {
            Thread.currentThread().interrupt();
            assertFalse(MysqlPlayerDataStore.awaitPendingWrites(1000));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static Field sourceField() throws Exception {
        Field field = MysqlPlayerDataStore.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        return field;
    }
}

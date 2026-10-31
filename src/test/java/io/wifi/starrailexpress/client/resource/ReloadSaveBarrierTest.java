package io.wifi.starrailexpress.client.resource;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReloadSaveBarrierTest {
    static final class Tasks implements Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable runnable) { tasks.add(runnable); }
        void runAll() { while (!tasks.isEmpty()) tasks.remove().run(); }
    }

    @Test
    void reloadIsImmediateWithoutSaveAndPreservesOriginalFuture() {
        var barrier = new ReloadSaveBarrier();
        var expected = new CompletableFuture<String>();
        assertSame(expected, barrier.afterSave(() -> expected, Runnable::run));
    }

    @Test
    void waitsWithoutBlockingAndResumesOnClientExecutor() {
        var barrier = new ReloadSaveBarrier();
        var saves = new Tasks();
        var client = new Tasks();
        var calls = new AtomicInteger();
        assertTrue(barrier.start(() -> {}, saves));
        var next = barrier.afterSave(() -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture("loaded");
        }, client);
        assertFalse(next.isDone());
        assertEquals(0, calls.get());
        saves.runAll();
        assertEquals(0, calls.get(), "不能在保存线程启动下一次游戏重载");
        client.runAll();
        assertEquals("loaded", next.join());
        assertEquals(1, calls.get());
    }

    @Test
    void rejectsDuplicateSaveWhileOneIsActive() {
        var barrier = new ReloadSaveBarrier();
        var saves = new Tasks();
        assertTrue(barrier.start(() -> {}, saves));
        assertFalse(barrier.start(() -> fail("duplicate save"), saves));
        saves.runAll();
        assertTrue(barrier.start(() -> {}, saves));
        saves.runAll();
    }

    @Test
    void saveFailureDoesNotFailNextReload() {
        var barrier = new ReloadSaveBarrier();
        var saves = new Tasks();
        assertTrue(barrier.start(() -> { throw new IllegalStateException("disk full"); }, saves));
        var next = barrier.afterSave(() -> CompletableFuture.completedFuture(42), Runnable::run);
        saves.runAll();
        assertTrue(barrier.completion().isCompletedExceptionally());
        assertEquals(42, next.join());
    }

    @Test
    void rechecksIfAnotherSaveStartedBeforeQueuedClientTask() {
        var barrier = new ReloadSaveBarrier();
        var saves = new Tasks();
        var client = new Tasks();
        barrier.start(() -> {}, saves);
        var next = barrier.afterSave(() -> CompletableFuture.completedFuture("ok"), client);
        saves.runAll();
        barrier.start(() -> {}, saves);
        client.runAll();
        assertFalse(next.isDone());
        saves.runAll();
        client.runAll();
        assertEquals("ok", next.join());
    }

    @Test
    void propagatesReloadFailureAndKeepsSeparateCallers() {
        var barrier = new ReloadSaveBarrier();
        var saves = new Tasks();
        barrier.start(() -> {}, saves);
        var failed = barrier.afterSave(() -> CompletableFuture.failedFuture(new IllegalArgumentException()), Runnable::run);
        var okay = barrier.afterSave(() -> CompletableFuture.completedFuture(7), Runnable::run);
        saves.runAll();
        assertTrue(failed.isCompletedExceptionally());
        assertEquals(7, okay.join());
    }

    @Test
    void rejectedSaveExecutorDoesNotLeavePermanentBarrier() {
        var barrier = new ReloadSaveBarrier();
        assertThrows(IllegalStateException.class, () -> barrier.start(() -> {}, task -> {
            throw new IllegalStateException("executor stopped");
        }));
        assertEquals(9, barrier.afterSave(() -> CompletableFuture.completedFuture(9), Runnable::run).join());
    }
}

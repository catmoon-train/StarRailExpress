package io.wifi.starrailexpress.client.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

class SkinDownloadRetryTest {
    private final SkinDownloadRetry policy = new SkinDownloadRetry();
    private final List<Long> delays = new ArrayList<>();
    private final List<Runnable> tasks = new ArrayList<>();
    private final AtomicInteger retries = new AtomicInteger();
    private final BiConsumer<Long, Runnable> scheduler = (delay, task) -> {
        delays.add(delay);
        tasks.add(task);
    };

    private void observe(CompletableFuture<?> download) {
        policy.observe(download, retries::incrementAndGet, scheduler);
    }

    @Test void pendingDownloadIsNotRestarted() {
        observe(new CompletableFuture<>());
        assertTrue(tasks.isEmpty());
        assertEquals(0, retries.get());
    }

    @Test void swallowedDownloadFailureRetriesAfterBackoff() {
        var download = new CompletableFuture<Void>();
        observe(download);
        // 原版吞掉 HTTP 异常，下载任务仍然正常完成，但没有皮肤成功回调。
        download.complete(null);
        assertEquals(List.of(5L), delays);
        assertEquals(0, retries.get());
        tasks.removeFirst().run();
        assertEquals(1, retries.get());
    }

    @Test void exceptionalDownloadAlsoRetries() {
        observe(CompletableFuture.failedFuture(new IllegalStateException("network")));
        tasks.removeFirst().run();
        assertEquals(1, retries.get());
    }

    @Test void repeatedLoadDoesNotScheduleDuplicateRetries() {
        var download = CompletableFuture.completedFuture(null);
        observe(download);
        observe(download);
        assertEquals(1, tasks.size());
    }

    @Test void successBeforeDownloadEndsDoesNotScheduleRetry() {
        var download = new CompletableFuture<Void>();
        observe(download);
        policy.markSucceeded();
        download.complete(null);
        assertTrue(tasks.isEmpty());
    }

    @Test void delayedDecodeSuccessCancelsScheduledRetry() {
        observe(CompletableFuture.completedFuture(null));
        policy.markSucceeded();
        tasks.removeFirst().run();
        assertEquals(0, retries.get());
        observe(CompletableFuture.completedFuture(null));
        assertTrue(tasks.isEmpty());
    }

    @Test void retriesAreBoundedAndBackOff() {
        for (int i = 0; i < 3; i++) {
            observe(CompletableFuture.completedFuture(null));
            tasks.removeFirst().run();
        }
        observe(CompletableFuture.completedFuture(null));
        assertEquals(List.of(5L, 15L, 45L), delays);
        assertEquals(3, retries.get());
        assertTrue(tasks.isEmpty());
    }

    @Test void staleCompletionCannotRestartNewerDownload() {
        var old = new CompletableFuture<Void>();
        var current = new CompletableFuture<Void>();
        observe(old);
        observe(current);
        old.complete(null);
        tasks.removeFirst().run();
        assertEquals(0, retries.get());
        current.complete(null);
        tasks.removeFirst().run();
        assertEquals(1, retries.get());
    }

    @Test void cacheHitWithoutDownloadDoesNotRetry() {
        observe(null);
        assertTrue(tasks.isEmpty());
    }
}

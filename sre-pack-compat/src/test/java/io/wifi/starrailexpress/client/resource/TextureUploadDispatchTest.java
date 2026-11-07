package io.wifi.starrailexpress.client.resource;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TextureUploadDispatchTest {
    @Test
    void renderThreadUploadsImmediatelyWithoutQueueing() {
        var uploads = new AtomicInteger();
        TextureUploadDispatch.dispatch(uploads::incrementAndGet, true, task -> fail("不应排队"));
        assertEquals(1, uploads.get());
    }

    @Test
    void otherThreadsWaitForRenderQueueReplay() {
        Queue<Runnable> renderQueue = new ArrayDeque<>();
        var uploads = new AtomicInteger();
        TextureUploadDispatch.dispatch(uploads::incrementAndGet, false, renderQueue::add);
        assertEquals(0, uploads.get());
        assertEquals(1, renderQueue.size());
        renderQueue.remove().run();
        assertEquals(1, uploads.get());
        assertTrue(renderQueue.isEmpty());
    }

    @Test
    void discardingGameTasksCannotDiscardQueuedRenderUpload() {
        Queue<Runnable> gameQueue = new ArrayDeque<>();
        Queue<Runnable> renderQueue = new ArrayDeque<>();
        var uploads = new AtomicInteger();
        var publishedSkin = CompletableFuture.completedFuture("skin");
        gameQueue.add(() -> fail("模拟被清理的默认纹理加载"));
        TextureUploadDispatch.dispatch(uploads::incrementAndGet, false, renderQueue::add);
        gameQueue.clear();
        assertTrue(publishedSkin.isDone());
        assertEquals(0, uploads.get());
        renderQueue.remove().run();
        assertEquals(1, uploads.get());
    }

    @Test
    void reentrantRenderUploadIsReadyBeforeGameQueueDiscard() {
        Queue<Runnable> gameQueue = new ArrayDeque<>();
        var uploads = new AtomicInteger();
        gameQueue.add(() -> fail("模拟被清理的默认纹理加载"));
        TextureUploadDispatch.dispatch(uploads::incrementAndGet, true, task -> fail("不应延后上传"));
        gameQueue.clear();
        assertEquals(1, uploads.get());
    }

    @Test
    void multipleQueuedUploadsKeepOrderAndRunOnce() {
        Queue<Runnable> renderQueue = new ArrayDeque<>();
        var order = new StringBuilder();
        TextureUploadDispatch.dispatch(() -> order.append('a'), false, renderQueue::add);
        TextureUploadDispatch.dispatch(() -> order.append('b'), false, renderQueue::add);
        assertEquals("", order.toString());
        while (!renderQueue.isEmpty()) renderQueue.remove().run();
        assertEquals("ab", order.toString());
    }

    @Test
    void immediateUploadFailurePropagatesUnchanged() {
        var failure = new IllegalStateException("upload failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> TextureUploadDispatch.dispatch(() -> { throw failure; }, true, task -> fail("不应重试"))));
    }

    @Test
    void renderQueueRejectionDoesNotRetryOrRunOnWrongThread() {
        var uploads = new AtomicInteger();
        var failure = new IllegalStateException("queue rejected");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> TextureUploadDispatch.dispatch(uploads::incrementAndGet, false, task -> { throw failure; })));
        assertEquals(0, uploads.get());
    }

    @Test
    void queuedUploadFailureIsNotSwallowedOnReplay() {
        Queue<Runnable> renderQueue = new ArrayDeque<>();
        var failure = new IllegalArgumentException("bad image");
        TextureUploadDispatch.dispatch(() -> { throw failure; }, false, renderQueue::add);
        assertSame(failure, assertThrows(IllegalArgumentException.class, renderQueue.remove()::run));
        assertTrue(renderQueue.isEmpty());
    }
}

package io.wifi.starrailexpress.mixin.compat.lightspeed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GlobalCacheMixinTest {
    private Method guard;

    @BeforeEach
    void resetProcessState() throws Exception {
        var field = GlobalCacheMixin.class.getDeclaredField("sre$startupCacheFinishStarted");
        field.setAccessible(true);
        ((AtomicBoolean) field.get(null)).set(false);
        guard = GlobalCacheMixin.class.getDeclaredMethod("sre$finishStartupCachesOnce", CallbackInfo.class);
        guard.setAccessible(true);
    }

    private boolean mayFinish() throws Exception {
        var ci = new CallbackInfo("finishStartupCaches", true);
        guard.invoke(null, ci);
        return !ci.isCancelled();
    }

    @Test
    void integratedServerThenVanillaTitleNeverResubmitsToClosedPool() throws Exception {
        var writes = new AtomicInteger();
        try (var startupExecutor = Executors.newSingleThreadExecutor()) {
            assertTrue(mayFinish(), "集成服首次收尾必须保留");
            var persistence = CompletableFuture.runAsync(writes::incrementAndGet, startupExecutor);
            persistence.get(5, TimeUnit.SECONDS);
            startupExecutor.shutdown();
            for (int visit = 0; visit < 100; visit++) {
                if (mayFinish()) {
                    fail("退出训练场后返回原版菜单不能再次向关闭的线程池提交任务");
                }
            }
            assertEquals(1, writes.get());
        }
    }

    @Test
    void titleFirstAlsoBlocksLaterServerAndTitleCalls() throws Exception {
        assertTrue(mayFinish(), "先打开原版标题页时也必须正常收尾");
        assertFalse(mayFinish());
        assertFalse(mayFinish());
    }

    @Test
    void concurrentCallsAllowOnlyOneBeforePersistenceCompletes() throws Exception {
        var start = new CountDownLatch(1);
        var allowed = new AtomicInteger();
        try (var callers = Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 32; i++) {
                tasks.add(callers.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    if (mayFinish()) allowed.incrementAndGet();
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, allowed.get());
        assertFalse(mayFinish());
    }
}

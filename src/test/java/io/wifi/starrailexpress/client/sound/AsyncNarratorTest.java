package io.wifi.starrailexpress.client.sound;

import com.mojang.text2speech.Narrator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AsyncNarratorTest {
    @Test
    void slowColdStartDoesNotBlockCallerAndKeepsNativeCallsOnOneThread() throws Exception {
        CountDownLatch initializing = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch destroyed = new CountDownLatch(1);
        List<String> calls = new CopyOnWriteArrayList<>();
        List<Thread> threads = new CopyOnWriteArrayList<>();
        Thread caller = Thread.currentThread();
        AsyncNarrator narrator = new AsyncNarrator(() -> {
            threads.add(Thread.currentThread());
            initializing.countDown();
            try {
                if (!ready.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("初始化未解除阻塞");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return new Narrator() {
                @Override
                public void say(String message, boolean interrupt) {
                    threads.add(Thread.currentThread());
                    calls.add(message + ":" + interrupt);
                }

                @Override
                public void clear() {
                    threads.add(Thread.currentThread());
                    calls.add("clear");
                }

                @Override
                public boolean active() {
                    threads.add(Thread.currentThread());
                    return true;
                }

                @Override
                public void destroy() {
                    threads.add(Thread.currentThread());
                    calls.add("destroy");
                    destroyed.countDown();
                }
            };
        });
        try {
            narrator.initialize();
            assertTrue(initializing.await(5, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                narrator.say("第一句", false);
                narrator.say("打断", true);
                narrator.clear();
                assertFalse(narrator.active());
                narrator.destroy();
                narrator.destroy();
                narrator.say("退出后不再播放", false);
            });
            assertTrue(calls.isEmpty(), "初始化期间不能执行任何原生播报");
        } finally {
            ready.countDown();
            narrator.destroy();
        }
        assertTrue(destroyed.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("第一句:false", "打断:true", "clear", "clear", "destroy"), calls);
        assertNotSame(caller, threads.getFirst());
        assertTrue(threads.stream().allMatch(thread -> thread == threads.getFirst()));
        assertTrue(threads.getFirst().isDaemon());
    }

    @Test
    void destroyingUnusedNarratorDoesNotInitializeNativeEngine() {
        AsyncNarrator narrator = new AsyncNarrator(() -> {
            fail("关闭未使用的播放器不应初始化系统语音");
            return Narrator.EMPTY;
        });
        narrator.destroy();
        narrator.initialize();
        narrator.say("忽略", false);
        assertFalse(narrator.active());
    }
}

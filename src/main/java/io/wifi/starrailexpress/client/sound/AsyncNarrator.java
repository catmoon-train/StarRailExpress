package io.wifi.starrailexpress.client.sound;

import com.mojang.text2speech.Narrator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 将系统语音的冷启动和原生调用隔离到固定线程，避免首句播报冻结游戏画面。
 * Windows SAPI 的 COM 对象必须在同一线程创建、使用和释放，不能使用通用线程池。
 */
public final class AsyncNarrator implements Narrator {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "SRE-Narrator");
        thread.setDaemon(true);
        return thread;
    });
    private final Supplier<Narrator> factory;
    // 原生实例只允许在专用线程访问；active 只是供客户端读取的状态快照。
    private Narrator delegate;
    private volatile boolean active;
    private boolean closed;

    public AsyncNarrator() {
        this(Narrator::getNarrator);
    }

    AsyncNarrator(Supplier<Narrator> factory) {
        this.factory = factory;
    }

    /** 后台提前创建语音引擎，不播放测试音，也不等待初始化完成。 */
    public void initialize() {
        submit(() -> {});
    }

    @Override
    public void say(String message, boolean interrupt) {
        submit(() -> delegate.say(message, interrupt));
    }

    @Override
    public void clear() {
        submit(() -> {
            if (active) {
                delegate.clear();
            }
        });
    }

    @Override
    public boolean active() {
        return active;
    }

    private synchronized void submit(Runnable action) {
        if (closed) {
            return;
        }
        executor.execute(() -> {
            try {
                if (delegate == null) {
                    delegate = factory.get();
                    active = delegate.active();
                }
                action.run();
            } catch (RuntimeException | LinkageError e) {
                LOGGER.error("系统语音后台调用失败", e);
            }
        });
    }

    /** 不阻塞游戏退出；排队的操作与 COM 释放仍在同一线程执行。 */
    @Override
    public synchronized void destroy() {
        if (closed) {
            return;
        }
        closed = true;
        executor.execute(() -> {
            try {
                if (delegate != null) {
                    try {
                        delegate.clear();
                    } finally {
                        delegate.destroy();
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                LOGGER.error("系统语音后台释放失败", e);
            } finally {
                active = false;
            }
        });
        executor.shutdown();
    }
}

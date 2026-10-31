package io.wifi.starrailexpress.client.resource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** 缓存保存期间异步延后资源重载，不阻塞渲染线程，也不合并或丢弃调用方的 future。 */
public final class ReloadSaveBarrier {
    private CompletableFuture<Void> saving = CompletableFuture.completedFuture(null);

    public synchronized boolean start(Runnable save, Executor executor) {
        if (!saving.isDone()) {
            return false;
        }
        // 先发布未完成状态，确保任务启动和完成的速度都不会绕过屏障。
        CompletableFuture<Void> current = new CompletableFuture<>();
        saving = current;
        try {
            executor.execute(() -> {
                try {
                    save.run();
                    current.complete(null);
                } catch (Throwable error) {
                    current.completeExceptionally(error);
                }
            });
        } catch (RuntimeException error) {
            current.completeExceptionally(error);
            throw error;
        }
        return true;
    }

    public <T> CompletableFuture<T> afterSave(Supplier<CompletableFuture<T>> reload, Executor clientExecutor) {
        CompletableFuture<Void> snapshot;
        synchronized (this) {
            snapshot = saving;
        }
        if (snapshot.isDone()) {
            return reload.get();
        }
        // 缓存保存失败不能阻止下一次正常重载；任务本身的失败仍由调用者记录。
        return snapshot.handle((ignored, error) -> null)
                .thenComposeAsync(ignored -> afterSave(reload, clientExecutor), clientExecutor);
    }

    public synchronized CompletableFuture<Void> completion() {
        return saving;
    }
}

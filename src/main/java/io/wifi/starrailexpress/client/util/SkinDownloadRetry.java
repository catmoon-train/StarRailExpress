package io.wifi.starrailexpress.client.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** 保留原纹理及其结果引用，仅对未成功的下载进行有限退避重试。 */
public final class SkinDownloadRetry {
    private static final long[] DELAYS_SECONDS = {5, 15, 45};
    private CompletableFuture<?> watched;
    private volatile boolean succeeded;
    private int retries;

    public void markSucceeded() {
        succeeded = true;
    }

    /** observe 和延迟任务均在渲染线程调用；下载完成通知可来自后台线程。 */
    public void observe(CompletableFuture<?> download, Runnable retry,
                        BiConsumer<Long, Runnable> schedule) {
        if (download == null || download == watched || succeeded || retries >= DELAYS_SECONDS.length) {
            return;
        }
        watched = download;
        long delay = DELAYS_SECONDS[retries];
        download.whenComplete((unused, error) -> {
            if (!succeeded) {
                // 下载任务结束不等于解码完成，延迟后在渲染线程再次确认。
                schedule.accept(delay, () -> {
                    if (!succeeded && watched == download && retries < DELAYS_SECONDS.length) {
                        retries++;
                        retry.run();
                    }
                });
            }
        });
    }
}

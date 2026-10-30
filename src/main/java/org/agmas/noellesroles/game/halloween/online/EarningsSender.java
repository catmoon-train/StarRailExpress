package org.agmas.noellesroles.game.halloween.online;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** 纯 Java 批量发送器：服务器线程只写 WAL，数据库事务仅在单一后台线程运行。 */
public final class EarningsSender implements AutoCloseable {
    private final EarningsStore store;
    private final EarningsSink sink;
    private final OnlineConfig config;
    private final LongSupplier clock;
    private final LongSupplier jitter;
    private final Consumer<String> warning;
    private final AtomicBoolean expedite = new AtomicBoolean();
    private ScheduledExecutorService worker;
    private List<Earning> batch;
    private long retryAt;
    private int failures;
    private long lastWarning = Long.MIN_VALUE;

    public EarningsSender(EarningsStore store, EarningsSink sink, OnlineConfig config,
                          LongSupplier clock, LongSupplier jitter, Consumer<String> warning) {
        this.store = store;
        this.sink = sink;
        this.config = config;
        this.clock = clock;
        this.jitter = jitter;
        this.warning = warning;
    }

    public void record(Earning entry) throws IOException { store.record(entry); }
    public void requestFlush() { if (store.pendingCount() > 0) expedite.set(true); }

    public void start() {
        if (worker != null) throw new IllegalStateException("Sender already started");
        worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "halloween-online-outbox");
            thread.setDaemon(true);
            return thread;
        });
        worker.scheduleWithFixedDelay(this::tick, 0, 250, TimeUnit.MILLISECONDS);
    }

    /** 返回是否完成一个批次。调用者不能是 MC tick；互斥保证任何情况下最多一个请求在途。 */
    public synchronized boolean tick() {
        long now = clock.getAsLong();
        if (now < retryAt || Thread.currentThread().isInterrupted()) return false;
        try {
            if (batch == null) {
                List<Earning> entries = store.pending(config.batchSize);
                if (entries.isEmpty()) return false;
                boolean early = expedite.getAndSet(false);
                if (entries.size() < config.batchSize && !early
                        && now - entries.getFirst().earnedAt() < config.flushIntervalSeconds * 1000L) return false;
                batch = entries;
            }
            // 收益 ID / 来源世界 / 获得时间重试时不变；数据库 commit 后才有确认。
            Set<UUID> ack = new java.util.HashSet<>(sink.persist(store.serverId(), batch));
            Set<UUID> sent = new java.util.HashSet<>();
            for (Earning entry : batch) sent.add(entry.id());
            ack.retainAll(sent); // 包含并发新增事件的未知 ACK 也不能删除它。
            store.acknowledge(ack);
            if (batch.stream().anyMatch(entry -> store.isPending(entry.id())))
                throw new IOException("Database acknowledgement incomplete; retaining unacknowledged earnings");
            batch = null;
            failures = 0;
            retryAt = 0;
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException ex) {
            long base = Math.min(300_000L, 1000L << Math.min(failures, 9));
            failures = Math.min(failures + 1, 10);
            retryAt = clock.getAsLong() + Math.min(300_000L, base + Math.floorMod(jitter.getAsLong(), base / 4 + 1));
            if (lastWarning == Long.MIN_VALUE || now - lastWarning >= 60_000L) {
                // 只记录类别；异常/响应正文可能包含敏感数据，不能直接打印。
                warning.accept("Halloween database retry pending (" + ex.getClass().getSimpleName() + "); outbox retained");
                lastWarning = now;
            }
            return false;
        }
    }

    /** 最多等待三秒；未确认记录始终留盘，不能为关服无限等待数据库。 */
    @Override
    public void close() throws IOException {
        if (worker != null) {
            requestFlush();
            worker.execute(() -> {
                long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (System.nanoTime() < end && store.pendingCount() > 0) {
                    requestFlush();
                    if (!tick()) break;
                }
            });
            worker.shutdown();
            try {
                if (!worker.awaitTermination(3, TimeUnit.SECONDS)) worker.shutdownNow();
            } catch (InterruptedException ex) {
                worker.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        store.close();
    }
}

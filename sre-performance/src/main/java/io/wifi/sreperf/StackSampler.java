package io.wifi.sreperf;

import io.wifi.sreperf.core.SampleAccumulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/** 只用标准 Java 线程 API 的后台采样器，兼容没有原生 profiler 的 Android Java。 */
final class StackSampler implements AutoCloseable {
    private final SampleAccumulator samples = new SampleAccumulator(2000, 48);
    private final Thread renderThread;
    private final int intervalMs;
    private final RuntimeCounters counters;
    private final Thread worker;
    private volatile boolean running = true;
    private long overheadNanos, maxProbeNanos, probes, errors;
    private String lastError = "";

    StackSampler(Thread renderThread, int intervalMs, RuntimeCounters counters) {
        this.renderThread = renderThread;
        this.intervalMs = intervalMs;
        this.counters = counters;
        worker = new Thread(this::run, "SRE-Performance-Sampler");
        worker.setDaemon(true);
        worker.start();
    }

    private void run() {
        List<Thread> targets = List.of(renderThread);
        long nextDiscover = 0, nextMemory = 0;
        while (running) {
            long start = System.nanoTime();
            try {
                if (start >= nextDiscover) {
                    targets = discover();
                    nextDiscover = start + 5_000_000_000L;
                }
                if (start >= nextMemory) {
                    counters.sample(start);
                    nextMemory = start + 1_000_000_000L;
                }
                for (Thread target : targets) {
                    if (!running) break;
                    Thread.State state = target.getState();
                    if (state != Thread.State.TERMINATED) samples.record(target.getName(), state, target.getStackTrace());
                }
            } catch (RuntimeException | LinkageError e) {
                errors++;
                lastError = e.getClass().getSimpleName();
            }
            long elapsed = Math.max(0, System.nanoTime() - start);
            overheadNanos += elapsed;
            maxProbeNanos = Math.max(maxProbeNanos, elapsed);
            probes++;
            long remaining = intervalMs * 1_000_000L - elapsed;
            if (remaining > 0 && running) LockSupport.parkNanos(remaining);
            if (Thread.interrupted() && !running) break;
        }
    }

    private List<Thread> discover() {
        var result = new ArrayList<Thread>();
        result.add(renderThread);
        try {
            ThreadGroup group = Thread.currentThread().getThreadGroup();
            while (group.getParent() != null) group = group.getParent();
            Thread[] threads = new Thread[Math.min(2048, Math.max(128, group.activeCount() * 2))];
            int count = group.enumerate(threads, true);
            Arrays.stream(threads, 0, count).filter(t -> t != null && t != renderThread && t.isAlive())
                    .filter(t -> {
                        String name = t.getName().toLowerCase(java.util.Locale.ROOT);
                        return !name.startsWith("sre-performance") && (name.contains("sound") || name.contains("audio") || name.contains("openal"));
                    }).sorted(Comparator.comparing(Thread::getName)).limit(3).forEach(result::add);
        } catch (RuntimeException e) {
            errors++;
            lastError = e.getClass().getSimpleName();
        }
        return result;
    }

    @Override
    public void close() {
        running = false;
        samples.stop();
        worker.interrupt();
    }

    Map<String, Object> finish(Function<StackTraceElement, String> resolver) throws InterruptedException {
        close();
        worker.join(2000);
        Map<String, Object> result = samples.snapshot(resolver);
        result.put("intervalMs", intervalMs);
        result.put("overheadMs", overheadNanos / 1_000_000.0);
        result.put("maxProbeMs", maxProbeNanos / 1_000_000.0);
        result.put("probeCount", probes);
        result.put("errors", errors);
        result.put("lastErrorType", lastError);
        result.put("workerStopped", !worker.isAlive());
        return result;
    }
}

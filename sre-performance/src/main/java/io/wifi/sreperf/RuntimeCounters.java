package io.wifi.sreperf;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.wifi.sreperf.core.SampleAccumulator.row;

/** GC 使用会话首尾差值；管理接口不可用时保留 null，而不是伪造零开销。 */
final class RuntimeCounters {
    private final long startNanos;
    private final long renderThreadId;
    private final Map<String, long[]> startGc;
    private final ArrayList<Map<String, Object>> heap = new ArrayList<>();
    private ThreadMXBean threadBean;
    private Long startCpu;

    RuntimeCounters(long startNanos, Thread renderThread) {
        this.startNanos = startNanos;
        renderThreadId = renderThread.threadId();
        startGc = gc();
        try {
            threadBean = ManagementFactory.getThreadMXBean();
            if (threadBean.isThreadCpuTimeSupported() && threadBean.isThreadCpuTimeEnabled()) {
                long value = threadBean.getThreadCpuTime(renderThreadId);
                if (value >= 0) startCpu = value;
            }
        } catch (RuntimeException | LinkageError ignored) { threadBean = null; }
        sample(startNanos);
    }

    synchronized void sample(long now) {
        if (heap.size() >= 256) return;
        var runtime = Runtime.getRuntime();
        heap.add(row("offsetMs", Math.max(0, now - startNanos) / 1_000_000.0,
                "usedBytes", runtime.totalMemory() - runtime.freeMemory(),
                "committedBytes", runtime.totalMemory(), "maxBytes", runtime.maxMemory()));
    }

    synchronized Map<String, Object> finish(long endNanos) {
        sample(endNanos);
        var endGc = gc();
        var collectors = new ArrayList<Map<String, Object>>();
        for (var e : startGc.entrySet()) {
            long[] end = endGc.get(e.getKey()), start = e.getValue();
            Long count = end == null || start[0] < 0 || end[0] < start[0] ? null : end[0] - start[0];
            Long millis = end == null || start[1] < 0 || end[1] < start[1] ? null : end[1] - start[1];
            collectors.add(row("name", e.getKey(), "collectionsDuringCapture", count, "collectionTimeMsDuringCapture", millis));
        }
        Long cpuNanos = null;
        try {
            if (threadBean != null && startCpu != null) {
                long end = threadBean.getThreadCpuTime(renderThreadId);
                if (end >= startCpu) cpuNanos = end - startCpu;
            }
        } catch (RuntimeException | LinkageError ignored) { }
        return row("available", !startGc.isEmpty() && !endGc.isEmpty(), "collectors", collectors,
                "heapSamples", new ArrayList<>(heap), "renderThreadCpuMs", cpuNanos == null ? null : cpuNanos / 1_000_000.0,
                "durationMs", Math.max(0, endNanos - startNanos) / 1_000_000.0,
                "scope", "GC counter deltas for this capture only; GC collection time is not a per-event pause trace.");
    }

    private static Map<String, long[]> gc() {
        var result = new LinkedHashMap<String, long[]>();
        try {
            for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                result.put(bean.getName(), new long[]{bean.getCollectionCount(), bean.getCollectionTime()});
            }
        } catch (RuntimeException | LinkageError ignored) { result.clear(); }
        return result;
    }
}

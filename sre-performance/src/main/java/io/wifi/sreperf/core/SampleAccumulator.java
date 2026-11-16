package io.wifi.sreperf.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 有界原始栈计数；与帧计时独立，导出时才解析模组来源。 */
public final class SampleAccumulator {
    private record Key(String thread, Thread.State state, List<StackTraceElement> frames) { }
    private final int maxStacks;
    private final int maxDepth;
    private final Map<Key, Long> stacks = new HashMap<>();
    private final Map<String, long[]> threads = new LinkedHashMap<>();
    private long attempts, recorded, dropped, truncated;
    private boolean accepting = true;

    public SampleAccumulator(int maxStacks, int maxDepth) {
        if (maxStacks < 1 || maxDepth < 1) throw new IllegalArgumentException("positive limits required");
        this.maxStacks = maxStacks;
        this.maxDepth = maxDepth;
    }

    public synchronized void record(String thread, Thread.State state, StackTraceElement[] frames) {
        if (!accepting) return;
        attempts++;
        if (!threads.containsKey(thread) && threads.size() >= 64) { dropped++; return; }
        long[] counts = threads.computeIfAbsent(thread, k -> new long[Thread.State.values().length]);
        counts[state.ordinal()]++;
        if (frames.length == 0) { dropped++; return; }
        if (frames.length > maxDepth) truncated++;
        Key key = new Key(thread, state, List.copyOf(Arrays.asList(Arrays.copyOf(frames, Math.min(maxDepth, frames.length)))));
        Long previous = stacks.get(key);
        if (previous == null && stacks.size() >= maxStacks) { dropped++; return; }
        stacks.put(key, previous == null ? 1L : previous + 1L);
        recorded++;
    }

    public synchronized void stop() { accepting = false; }

    public synchronized Map<String, Object> snapshot(Function<StackTraceElement, String> resolver) {
        var threadRows = new ArrayList<Map<String, Object>>();
        for (var entry : threads.entrySet()) {
            var states = new LinkedHashMap<String, Long>();
            long total = 0;
            for (var state : Thread.State.values()) {
                long n = entry.getValue()[state.ordinal()];
                states.put(state.name(), n); total += n;
            }
            threadRows.add(row("name", entry.getKey(), "samples", total, "states", states));
        }
        long unattributed = 0;
        var mods = new HashMap<String, long[]>();
        var hotspots = new HashMap<String, Long>();
        var rows = new ArrayList<Map<String, Object>>();
        var ordered = stacks.entrySet().stream().sorted(Map.Entry.<Key, Long>comparingByValue().reversed()).toList();
        for (var entry : ordered) {
            Key key = entry.getKey(); long count = entry.getValue();
            String nearest = null;
            var included = new HashSet<String>();
            for (var frame : key.frames) {
                String owner = resolver.apply(frame);
                if (owner != null && owner.startsWith("mod:")) {
                    String id = owner.substring(4);
                    included.add(id);
                    if (nearest == null) nearest = id;
                }
            }
            if (nearest == null) unattributed += count;
            for (String id : included) {
                long[] values = mods.computeIfAbsent(key.thread + "\u0000" + id, k -> new long[4]);
                values[1] += count;
                if (id.equals(nearest)) {
                    values[0] += count;
                    if (key.state == Thread.State.RUNNABLE) values[2] += count;
                    else values[3] += count;
                }
            }
            String leaf = key.frames.getFirst().getClassName() + "." + key.frames.getFirst().getMethodName();
            hotspots.merge(key.thread + "\u0000" + key.state + "\u0000" + leaf, count, Long::sum);
            if (rows.size() < 100) {
                rows.add(row("thread", key.thread, "state", key.state.name(), "samples", count,
                        "nearestMod", nearest == null ? "unresolved_or_vanilla" : nearest,
                        "frames", key.frames.stream().map(f -> f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber()).toList()));
            }
        }
        var modRows = new ArrayList<Map<String, Object>>();
        for (var entry : mods.entrySet()) {
            String[] parts = entry.getKey().split("\u0000", -1); long[] n = entry.getValue();
            long denominator = Arrays.stream(threads.get(parts[0])).sum();
            modRows.add(row("thread", parts[0], "mod", parts[1], "nearestPathSamples", n[0],
                    "inclusivePathSamples", n[1], "runnableSamples", n[2], "nonRunnableSamples", n[3],
                    "shareOfThreadSamplesPercent", denominator == 0 ? null : 100.0 * n[0] / denominator));
        }
        modRows.sort(Comparator.comparingLong((Map<String, Object> r) -> ((Number) r.get("nearestPathSamples")).longValue()).reversed());
        var hotRows = new ArrayList<Map<String, Object>>();
        hotspots.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(150).forEach(e -> {
            String[] parts = e.getKey().split("\u0000", -1);
            hotRows.add(row("thread", parts[0], "state", parts[1], "method", parts[2], "samples", e.getValue()));
        });
        return row("attemptedSamples", attempts, "recordedSamples", recorded, "droppedSamples", dropped,
                "truncatedStacks", truncated, "uniqueStacks", stacks.size(), "threads", threadRows,
                "mods", modRows, "hotspots", hotRows, "stacks", rows, "unattributedSamples", unattributed,
                "attribution", "Nearest/inclusive mod on the sampled call path; not exclusive CPU cost or removal benefit. Observer, loader, MixinExtras and Fabric API infrastructure are excluded; raw stacks are retained.");
    }

    public static Map<String, Object> row(Object... pairs) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}

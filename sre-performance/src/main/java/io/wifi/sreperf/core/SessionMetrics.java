package io.wifi.sreperf.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 主线程独占的有界计时器；停止并结束当前帧后再获取快照。 */
public final class SessionMetrics {
    public static final String FRAME = "FRAME";
    private static final int MAX_DEPTH = 64;
    private static final int MAX_PHASES = 32;
    private static final double NANOS_PER_MS = 1_000_000.0;

    private final Frame[] frames;
    private final String[] names = new String[MAX_PHASES];
    private final long[] calls = new long[MAX_PHASES];
    private final long[] totals = new long[MAX_PHASES];
    private final long[] selves = new long[MAX_PHASES];
    private final long[] maxima = new long[MAX_PHASES];
    private final long[] bytes = new long[MAX_PHASES];
    private final long[] pendingCalls = new long[MAX_PHASES];
    private final long[] pendingTotals = new long[MAX_PHASES];
    private final long[] pendingSelves = new long[MAX_PHASES];
    private final long[] pendingMaxima = new long[MAX_PHASES];
    private final long[] pendingBytes = new long[MAX_PHASES];
    private final int[] activeNames = new int[MAX_PHASES];
    private final int[] stackPhases = new int[MAX_DEPTH];
    private final long[] stackTokens = new long[MAX_DEPTH];
    private final long[] stackStarts = new long[MAX_DEPTH];
    private final long[] stackChildren = new long[MAX_DEPTH];
    private int phaseCount = 1;
    private int count;
    private int depth;
    private long nextToken = 1;
    private long origin;
    private long lastEvent;
    private long phaseMismatches;
    private long depthOverflows;
    private long phaseOverflows;
    private long timingAnomalies;
    private Frame current;

    public SessionMetrics(int maxFrames) {
        if (maxFrames <= 0) {
            throw new IllegalArgumentException("maxFrames must be positive");
        }
        frames = new Frame[maxFrames];
        names[0] = FRAME;
    }

    public void frameStart(long nowNanos, boolean focused, boolean inWorld, boolean screenOpen) {
        if (isFull()) {
            return;
        }
        if (current != null) {
            phaseMismatches++;
            frameEnd(nowNanos);
            if (isFull()) {
                return;
            }
        }
        long observedStart = nowNanos;
        boolean backwards = count > 0 && nowNanos - lastEvent < 0;
        if (count == 0) {
            origin = nowNanos;
            lastEvent = nowNanos;
        } else {
            nowNanos = monotonic(nowNanos);
        }
        current = new Frame(observedStart, focused, inWorld, screenOpen);
        if (count > 0) {
            long interval = observedStart - frames[count - 1].start;
            if (interval > 0 && !backwards) {
                current.interval = interval;
            } else if (!backwards) {
                timingAnomalies++;
                phaseMismatches++;
            }
        }
        push(0, 0, nowNanos);
    }

    public void frameEnd(long nowNanos) {
        if (current == null) {
            if (!isFull()) {
                phaseMismatches++;
            }
            return;
        }
        nowNanos = monotonic(nowNanos);
        while (depth > 1) {
            phaseMismatches++;
            closeTop(nowNanos, 0);
        }
        closeTop(nowNanos, 0);
        for (int id = 0; id < phaseCount; id++) {
            calls[id] = add(calls[id], pendingCalls[id]);
            totals[id] = add(totals[id], pendingTotals[id]);
            selves[id] = add(selves[id], pendingSelves[id]);
            maxima[id] = Math.max(maxima[id], pendingMaxima[id]);
            bytes[id] = add(bytes[id], pendingBytes[id]);
            pendingCalls[id] = pendingTotals[id] = pendingSelves[id] = pendingMaxima[id] = pendingBytes[id] = 0;
        }
        current.work = nowNanos - stackStarts[0];
        frames[count++] = current;
        current = null;
    }

    /** 返回的非零标识对应一次入栈；FRAME 是保留名称，根作用域已自动管理。 */
    public long begin(String phase, long nowNanos) {
        if (current == null || isFull()) {
            return 0;
        }
        if (phase == null || phase.isBlank() || FRAME.equals(phase)) {
            phaseMismatches++;
            return 0;
        }
        if (depth == MAX_DEPTH) {
            depthOverflows++;
            return 0;
        }
        int id = findPhase(phase);
        if (id < 0) {
            if (phaseCount == MAX_PHASES) {
                phaseOverflows++;
                return 0;
            }
            id = phaseCount++;
            names[id] = phase;
        }
        nowNanos = monotonic(nowNanos);
        long token = nextToken++;
        if (token == 0) {
            token = nextToken++;
        }
        push(id, token, nowNanos);
        return token;
    }

    public void end(String phase, long token, long nowNanos, long byteCount) {
        if (token == 0) {
            return;
        }
        int index = depth - 1;
        while (index > 0 && stackTokens[index] != token) {
            index--;
        }
        if (index <= 0 || !names[stackPhases[index]].equals(phase)) {
            phaseMismatches++;
            return;
        }
        nowNanos = monotonic(nowNanos);
        // 乱序退出时在同一时刻截断未关闭的子作用域，避免重叠或负 self。
        while (depth - 1 > index) {
            phaseMismatches++;
            closeTop(nowNanos, 0);
        }
        if (byteCount < 0) {
            phaseMismatches++;
            byteCount = 0;
        }
        closeTop(nowNanos, byteCount);
    }

    /** 只计已结束并保存的帧；未结束的当前帧不计入统计。 */
    public int frameCount() {
        return count;
    }

    public boolean isFull() {
        return count == frames.length;
    }

    /** 所有返回容器均为新建副本；分位数使用 nearest-rank，单位为毫秒。 */
    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary(false));
        result.put("foregroundWorld", summary(true));
        List<Map<String, Object>> phases = new ArrayList<>();
        for (int id = 0; id < phaseCount; id++) {
            if (calls[id] == 0) {
                continue;
            }
            Map<String, Object> phase = new LinkedHashMap<>();
            phase.put("name", names[id]);
            phase.put("calls", calls[id]);
            phase.put("totalMs", totals[id] / NANOS_PER_MS);
            phase.put("selfMs", selves[id] / NANOS_PER_MS);
            phase.put("maxMs", maxima[id] / NANOS_PER_MS);
            phase.put("bytes", bytes[id]);
            phases.add(phase);
        }
        result.put("phases", phases);
        List<Map<String, Object>> frameRows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Frame frame = frames[i];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", i);
            row.put("startOffsetMs", (frame.start - origin) / NANOS_PER_MS);
            row.put("intervalMs", frame.interval < 0 ? null : frame.interval / NANOS_PER_MS);
            row.put("workMs", frame.work / NANOS_PER_MS);
            row.put("focused", frame.focused);
            row.put("inWorld", frame.inWorld);
            row.put("screenOpen", frame.screenOpen);
            Map<String, Double> phaseTimes = new LinkedHashMap<>();
            for (int id = 0; id < phaseCount; id++) {
                if ((frame.seenNames & (1 << id)) != 0) {
                    phaseTimes.put(names[id], frame.phaseNanos[id] / NANOS_PER_MS);
                }
            }
            row.put("phasesMs", phaseTimes);
            frameRows.add(row);
        }
        result.put("frames", frameRows);
        result.put("phaseMismatches", phaseMismatches);
        result.put("depthOverflows", depthOverflows);
        result.put("phaseOverflows", phaseOverflows);
        result.put("timingAnomalies", timingAnomalies);
        result.put("incompleteFrame", current != null);
        return result;
    }

    private void push(int id, long token, long now) {
        stackPhases[depth] = id;
        stackTokens[depth] = token;
        stackStarts[depth] = now;
        stackChildren[depth] = 0;
        activeNames[id]++;
        depth++;
    }

    private void closeTop(long now, long byteCount) {
        int index = --depth;
        int id = stackPhases[index];
        long duration = now - stackStarts[index];
        long self = Math.max(0, duration - stackChildren[index]);
        pendingCalls[id]++;
        pendingTotals[id] = add(pendingTotals[id], duration);
        pendingSelves[id] = add(pendingSelves[id], self);
        pendingMaxima[id] = Math.max(pendingMaxima[id], duration);
        pendingBytes[id] = add(pendingBytes[id], byteCount);
        // 每帧同名递归只记录最外层区间的并集；不把嵌套 inclusive 相加当作帧工作量。
        if (--activeNames[id] == 0) {
            current.phaseNanos[id] = add(current.phaseNanos[id], duration);
            current.seenNames |= 1 << id;
        }
        if (depth > 0) {
            stackChildren[depth - 1] = add(stackChildren[depth - 1], duration);
        }
    }

    private int findPhase(String phase) {
        for (int id = 0; id < phaseCount; id++) {
            if (names[id].equals(phase)) {
                return id;
            }
        }
        return -1;
    }

    private long monotonic(long now) {
        if (now - lastEvent < 0) {
            timingAnomalies++;
            phaseMismatches++;
            return lastEvent;
        }
        lastEvent = now;
        return now;
    }

    private static long add(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    private Map<String, Object> summary(boolean foregroundOnly) {
        long[] intervals = new long[count];
        int intervalCount = 0;
        int selectedFrames = 0;
        int over50 = 0;
        int over100 = 0;
        double sum = 0;
        for (int i = 0; i < count; i++) {
            Frame frame = frames[i];
            if (!foregroundOnly || frame.foregroundWorld()) {
                selectedFrames++;
            }
            if (frame.interval < 0 || (foregroundOnly && (i == 0
                    || !frame.foregroundWorld() || !frames[i - 1].foregroundWorld()))) {
                continue;
            }
            long interval = frame.interval;
            intervals[intervalCount++] = interval;
            sum += interval;
            if (interval > 50_000_000L) {
                over50++;
            }
            if (interval > 100_000_000L) {
                over100++;
            }
        }
        Arrays.sort(intervals, 0, intervalCount);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("frameCount", selectedFrames);
        result.put("intervalCount", intervalCount);
        result.put("averageFps", intervalCount == 0 ? null : intervalCount * 1_000_000_000.0 / sum);
        result.put("p50Ms", percentile(intervals, intervalCount, 0.50));
        result.put("p95Ms", percentile(intervals, intervalCount, 0.95));
        result.put("p99Ms", percentile(intervals, intervalCount, 0.99));
        result.put("maxMs", intervalCount == 0 ? null : intervals[intervalCount - 1] / NANOS_PER_MS);
        result.put("over50Ms", over50);
        result.put("over100Ms", over100);
        result.put("totalIntervalMs", intervalCount == 0 ? null : sum / NANOS_PER_MS);
        return result;
    }

    private static Double percentile(long[] sorted, int size, double fraction) {
        return size == 0 ? null : sorted[(int) Math.ceil(size * fraction) - 1] / NANOS_PER_MS;
    }

    private static final class Frame {
        private final long start;
        private final boolean focused;
        private final boolean inWorld;
        private final boolean screenOpen;
        private final long[] phaseNanos = new long[MAX_PHASES];
        private long interval = -1;
        private long work;
        private int seenNames;

        private Frame(long start, boolean focused, boolean inWorld, boolean screenOpen) {
            this.start = start;
            this.focused = focused;
            this.inWorld = inWorld;
            this.screenOpen = screenOpen;
        }

        private boolean foregroundWorld() {
            return focused && inWorld && !screenOpen;
        }
    }
}

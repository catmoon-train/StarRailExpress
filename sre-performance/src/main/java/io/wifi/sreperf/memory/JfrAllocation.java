package io.wifi.sreperf.memory;

import io.wifi.sreperf.SourceResolver;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 可选 JFR 入口隔离在单独类中，缺少 jdk.jfr 的运行时仍能使用普通快照。 */
public final class JfrAllocation {
    public static final long MAX_BYTES = 32L * 1024 * 1024;
    private JfrAllocation() { }

    public static Map<String, Object> record(Path file, int seconds) throws Exception {
        if (seconds < 10 || seconds > 180) throw new IllegalArgumentException("JFR duration must be 10–180 seconds");
        if (!FlightRecorder.isAvailable() || FlightRecorder.getFlightRecorder().getEventTypes().stream()
                .map(EventType::getName).noneMatch("jdk.ObjectAllocationSample"::equals)) {
            throw new UnsupportedOperationException("JFR jdk.ObjectAllocationSample is unavailable on this JVM");
        }
        long started;
        long elapsed;
        try (Recording recording = new Recording()) {
            recording.setName("sre-performance-allocation");
            // 不加载 profile/default 配置，不启用完整分配事件、堆转储或 GC roots 跟踪。
            recording.enable("jdk.ObjectAllocationSample").withStackTrace().with("throttle", "100/s");
            recording.setToDisk(true);
            recording.setMaxSize(MAX_BYTES);
            recording.setMaxAge(Duration.ofSeconds(seconds + 5L));
            recording.setDuration(Duration.ofSeconds(seconds));
            started = System.nanoTime();
            recording.start();
            Thread.sleep(seconds * 1000L);
            if (recording.getState() == RecordingState.RUNNING) recording.stop();
            elapsed = System.nanoTime() - started;
            recording.dump(file);
        }
        Map<String, Object> out = summarize(file, new SourceResolver());
        out.put("supported", true);
        out.put("requestedSeconds", seconds);
        out.put("elapsedMs", elapsed / 1_000_000.0);
        out.put("maxRecordingBytes", MAX_BYTES);
        out.put("retention", "Rolling JFR repository cap 32 MiB / duration+5s; JVM chunk granularity can exceed cap, oldest chunks may be discarded. allocation.jfr contains only the retained window. Aggregation caps: 2000 classes/stacks, 32 frames.");
        return out;
    }

    static Map<String, Object> summarize(Path file, java.util.function.Function<StackTraceElement, String> resolver) throws Exception {
        AllocationSamples samples = new AllocationSamples(2000);
        try (RecordingFile recording = new RecordingFile(file)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                if (!event.getEventType().getName().equals("jdk.ObjectAllocationSample")) continue;
                List<String> stack = new ArrayList<>();
                String nearest = "unattributed";
                var trace = event.getStackTrace();
                boolean truncated = trace != null && trace.isTruncated();
                if (trace != null) {
                    truncated |= trace.getFrames().size() > 32;
                    for (var frame : trace.getFrames().subList(0, Math.min(32, trace.getFrames().size()))) {
                        var method = frame.getMethod();
                        StackTraceElement element = new StackTraceElement(method.getType().getName(), method.getName(), null, frame.getLineNumber());
                        stack.add(element.toString());
                        if (nearest.equals("unattributed")) {
                            String source = resolver.apply(element);
                            if (source != null && source.startsWith("mod:")) nearest = source;
                        }
                    }
                }
                samples.add(event.getClass("objectClass").getName(), stack, nearest, event.getLong("weight"), truncated);
            }
        }
        return samples.snapshot();
    }
}

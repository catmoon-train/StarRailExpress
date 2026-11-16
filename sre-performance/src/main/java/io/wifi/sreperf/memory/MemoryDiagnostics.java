package io.wifi.sreperf.memory;

import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** 客户端和服务端共用的按需任务入口，无客户端类、定时轮询或空闲后台线程。 */
public final class MemoryDiagnostics {
    public enum Kind { SNAPSHOT, HISTOGRAM, ALLOCATION }
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static volatile Thread worker;
    private static volatile String owner;

    private MemoryDiagnostics() { }

    /** world 必须由调用方在所属游戏线程提供；reply 必须自行调度回游戏线程。 */
    public static int request(Path base, String side, Kind kind, int seconds,
                              Supplier<Map<String, Object>> world, BiConsumer<String, Object[]> reply) {
        if (kind == Kind.ALLOCATION && (seconds < 10 || seconds > 180)) {
            throw new IllegalArgumentException("Allocation recording must be 10–180 seconds");
        }
        if (!BUSY.compareAndSet(false, true)) { reply.accept("sreperf.memory.busy", new Object[0]); return 0; }
        owner = side;
        Map<String, Object> loaded;
        long start = System.nanoTime();
        try {
            loaded = world.get();
        } catch (RuntimeException | LinkageError e) {
            owner = null;
            BUSY.set(false);
            reply.accept("sreperf.memory.failed", new Object[]{e.toString()});
            return 0;
        }
        double worldMs = (System.nanoTime() - start) / 1_000_000.0;
        reply.accept(switch (kind) {
            case SNAPSHOT -> "sreperf.memory.started";
            case HISTOGRAM -> "sreperf.memory.histogram_warning";
            case ALLOCATION -> "sreperf.memory.allocation_started";
        }, new Object[]{seconds});
        worker = new Thread(() -> {
            try {
                Path directory = MemoryReportWriter.createDirectory(base, kind.name().toLowerCase(java.util.Locale.ROOT));
                Map<String, Object> report = new LinkedHashMap<>();
                report.put("schemaVersion", 1);
                report.put("kind", kind.name());
                report.put("side", side);
                report.put("startedAt", Instant.now().toString());
                report.put("scope", "Current JVM process; integrated client/server share heap. No remote reads.");
                report.put("loadedWorld", loaded);
                report.put("worldSnapshotMs", worldMs);
                long jvmStart = System.nanoTime();
                report.put("jvm", JvmMemorySnapshot.capture());
                report.put("jvmSnapshotMs", (System.nanoTime() - jvmStart) / 1_000_000.0);
                report.put("limitations", MemoryReportWriter.LIMITS);
                String raw = null;
                String unavailable = null;
                if (kind != Kind.SNAPSHOT) {
                    String section = kind == Kind.HISTOGRAM ? "histogram" : "allocation";
                    long diagnosticStart = System.nanoTime();
                    try {
                        if (kind == Kind.HISTOGRAM) {
                            raw = ClassHistogram.capture();
                            report.put(section, ClassHistogram.parse(raw));
                        } else {
                            // 只在显式命令中解析可选 JFR 类；Android/精简 JVM 的链接错误转为明确反馈。
                            report.put(section, JfrAllocation.record(directory.resolve("allocation.jfr"), seconds));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw e;
                    } catch (Exception | LinkageError e) {
                        unavailable = e.toString();
                        report.put(section, Map.of("supported", false, "reason", unavailable));
                    }
                    report.put("diagnosticWallMs", (System.nanoTime() - diagnosticStart) / 1_000_000.0);
                }
                MemoryReportWriter.write(directory, report, raw);
                if (unavailable != null) reply.accept("sreperf.memory.unsupported", new Object[]{unavailable});
                reply.accept("sreperf.memory.saved", new Object[]{directory.toString()});
            } catch (InterruptedException e) {
                reply.accept("sreperf.memory.cancelled", new Object[0]);
            } catch (Exception | LinkageError e) {
                LoggerFactory.getLogger("sre-performance").warn("Memory diagnostics failed", e);
                reply.accept("sreperf.memory.failed", new Object[]{e.toString()});
            } finally {
                worker = null;
                owner = null;
                BUSY.set(false);
            }
        }, "sre-performance-memory");
        worker.setDaemon(true);
        worker.start();
        return 1;
    }

    public static void shutdown(String side) {
        Thread running = worker;
        if (side.equals(owner) && running != null) running.interrupt();
    }
}

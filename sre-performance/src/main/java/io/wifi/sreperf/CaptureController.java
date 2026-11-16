package io.wifi.sreperf;

import io.wifi.sreperf.core.SessionMetrics;
import io.wifi.sreperf.report.ReportWriter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.wifi.sreperf.core.SampleAccumulator.row;

/** 会话只能在客户端线程操作；停止在完整帧边界生效，报告在后台生成。 */
public final class CaptureController {
    private static final Logger LOGGER = LoggerFactory.getLogger("sre-performance");
    private static final ExecutorService EXPORTER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SRE-Performance-Export"); t.setDaemon(true); return t;
    });
    private static volatile Capture active;
    private static volatile boolean exporting;
    private static volatile Path lastReport;
    private static volatile String exportFailure;

    private CaptureController() { }

    public static boolean start(int seconds, int sampleIntervalMs) {
        if (seconds < 10 || seconds > 180 || sampleIntervalMs < 10 || sampleIntervalMs > 100) throw new IllegalArgumentException("capture bounds");
        if (active != null || exporting || EXPORTER.isShutdown()) return false;
        exportFailure = null;
        active = new Capture(seconds, sampleIntervalMs);
        return true;
    }

    public static boolean requestStop() {
        Capture c = active;
        if (c == null) return false;
        c.stopRequested = true;
        return true;
    }

    public static boolean mark(String text) {
        Capture c = active;
        if (c == null || c.marks.size() >= 64) return false;
        String safe = text.replace('\r', ' ').replace('\n', ' ');
        if (safe.length() > 120) safe = safe.substring(0, 120);
        c.marks.add(row("offsetMs", c.startNanos == 0 ? 0 : (System.nanoTime() - c.startNanos) / 1_000_000.0, "label", safe));
        return true;
    }

    public static boolean isCapturing() { return active != null; }
    public static boolean isExporting() { return exporting; }
    public static Path lastReport() { return lastReport; }
    public static String exportFailure() { return exportFailure; }
    public static int frames() { Capture c = active; return c == null ? 0 : c.metrics.frameCount(); }
    public static int elapsedSeconds() { Capture c = active; return c == null || c.startNanos == 0 ? 0 : (int) ((System.nanoTime() - c.startNanos) / 1_000_000_000L); }

    public static int frameStart() {
        Capture c = active;
        if (c == null) return 0;
        if (c.frameDepth > 0) { c.frameDepth++; return 2; }
        try {
            Minecraft client = Minecraft.getInstance();
            if (c.startNanos == 0) {
                c.owner = Thread.currentThread();
                c.environment = EnvironmentSnapshot.collect(client);
                c.startNanos = System.nanoTime();
                c.startedAt = Instant.now().toString();
                c.counters = new RuntimeCounters(c.startNanos, c.owner);
                c.sampler = new StackSampler(c.owner, c.intervalMs, c.counters);
            }
            c.frameDepth = 1;
            c.metrics.frameStart(System.nanoTime(), client.isWindowActive(), client.level != null, client.screen != null);
            return 1;
        } catch (RuntimeException | LinkageError e) {
            active = null;
            if (c.sampler != null) c.sampler.close();
            LOGGER.error("Could not start diagnostics", e);
            message("sreperf.failed", e.getClass().getSimpleName());
            return 0;
        }
    }

    public static void frameEnd(int ticket, boolean success) {
        Capture c = active;
        if (c == null || ticket == 0) return;
        if (ticket == 2) { c.frameDepth--; return; }
        c.metrics.frameEnd(System.nanoTime());
        c.frameDepth = 0;
        if (!success) c.warnings.add("A game frame threw an exception; its timing includes the failed frame.");
        if (!success || c.stopRequested || c.metrics.isFull() || System.nanoTime() - c.startNanos >= c.seconds * 1_000_000_000L) {
            finish(c, !success ? "frame_exception" : c.metrics.isFull() ? "frame_capacity" : c.stopRequested ? "manual" : "duration");
        }
    }

    public static long begin(String phase) {
        Capture c = active;
        return c == null || c.owner != Thread.currentThread() || c.frameDepth == 0 ? 0 : c.metrics.begin(phase, System.nanoTime());
    }

    public static void end(String phase, long token, long bytes) {
        if (token == 0) return;
        Capture c = active;
        if (c != null && c.owner == Thread.currentThread()) c.metrics.end(phase, token, System.nanoTime(), bytes);
    }

    private static void finish(Capture c, String reason) {
        if (active != c) return;
        active = null;
        if (c.startNanos == 0) { message("sreperf.cancelled"); return; }
        exporting = true;
        long end = System.nanoTime();
        c.sampler.close();
        Map<String, Object> gc = c.counters.finish(end);
        var client = Minecraft.getInstance();
        c.environment.put("capture", row("startedAt", c.startedAt, "durationMs", (end - c.startNanos) / 1_000_000.0,
                "requestedSeconds", c.seconds, "stopReason", reason, "maxFrames", 30000, "maxUniqueStacks", 2000,
                "endFramebufferWidth", client.getWindow().getWidth(), "endFramebufferHeight", client.getWindow().getHeight()));
        message("sreperf.exporting");
        EXPORTER.execute(() -> {
            try {
                var samples = c.sampler.finish(new SourceResolver());
                var metrics = c.metrics.snapshot();
                var warnings = new ArrayList<>(c.warnings);
                warnings.add("Stage timings are render-thread wall time, including native/driver waits; not GPU execution time.");
                warnings.add("Mod stack associations are samples, not exclusive mod cost or guaranteed removal benefit.");
                warnings.add("Sampler probe wall time measures its own work, not the full safepoint/cache impact on the observed game.");
                warnings.add("VertexBuffer/NativeImage hooks cover engine entrypoints; mods using their own direct GL uploads can bypass these counters.");
                if (((Number) samples.get("droppedSamples")).longValue() > 0) warnings.add("Some stacks were dropped due to empty stacks or the unique-stack limit.");
                if (((Number) samples.get("errors")).longValue() > 0) warnings.add("Some runtime sampling calls failed; inspect errors and lastErrorType.");
                if (!Boolean.TRUE.equals(samples.get("workerStopped"))) warnings.add("Sampler did not stop within 2 seconds; observer overhead is a partial snapshot.");
                if (reason.equals("frame_capacity")) warnings.add("Capture stopped early at the frame-capacity limit.");
                Map<String, Object> report = row("schemaVersion", 1, "environment", c.environment, "metrics", metrics,
                        "samples", samples, "gc", gc, "marks", new ArrayList<>(c.marks), "warnings", warnings);
                Path path = ReportWriter.write(FabricLoader.getInstance().getGameDir().resolve("sre-performance"), report);
                lastReport = path;
                LOGGER.info("Diagnostic report saved: {}", path);
                client.execute(() -> message("sreperf.saved", path.toString()));
            } catch (Exception | LinkageError e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                exportFailure = e.getClass().getSimpleName();
                LOGGER.error("Diagnostic report export failed", e);
                client.execute(() -> message("sreperf.failed", e.getClass().getSimpleName()));
            } finally { exporting = false; }
        });
    }

    static void shutdown() {
        Capture c = active;
        if (c != null) {
            if (c.frameDepth > 0) { c.metrics.frameEnd(System.nanoTime()); c.frameDepth = 0; c.warnings.add("Final frame was cut short by client shutdown."); }
            finish(c, "client_stopping");
        }
        EXPORTER.shutdown();
        try { if (!EXPORTER.awaitTermination(5, TimeUnit.SECONDS)) LOGGER.warn("Report is still exporting during shutdown; use /sreperf stop before exiting for a complete report."); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static void message(String key, Object... args) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null) client.gui.getChat().addMessage(Component.translatable(key, args));
    }

    private static final class Capture {
        final int seconds, intervalMs;
        final SessionMetrics metrics = new SessionMetrics(30000);
        final ArrayList<Map<String, Object>> marks = new ArrayList<>();
        final ArrayList<String> warnings = new ArrayList<>();
        boolean stopRequested;
        int frameDepth;
        long startNanos;
        String startedAt;
        Thread owner;
        RuntimeCounters counters;
        StackSampler sampler;
        Map<String, Object> environment;
        Capture(int seconds, int intervalMs) { this.seconds = seconds; this.intervalMs = intervalMs; }
    }
}

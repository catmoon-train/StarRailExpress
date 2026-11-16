package io.wifi.sreperf.smoke;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.sreperf.CaptureController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.zip.ZipFile;

/** 仅 smoke source set 打包；不进入发布 JAR，不连接服务器或打开用户存档。 */
public final class PerfSmoke implements ClientModInitializer {
    private long started;
    private boolean captureStarted;
    private int reports;
    private java.nio.file.Path previous;

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> started = System.nanoTime());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (started == 0) return;
            if (System.nanoTime() - started > 90_000_000_000L) throw new IllegalStateException("SRE_PERF_SMOKE_TIMEOUT");
            if (!captureStarted && client.getOverlay() == null && System.nanoTime() - started > 2_000_000_000L) {
                if (!CaptureController.start(10, 20)) throw new IllegalStateException("SRE_PERF_SMOKE_START_FAILED");
                CaptureController.mark("<script>smoke</script> 中文,\"marker\"");
                captureStarted = true;
            }
            if (reports == 1 && CaptureController.isCapturing() && CaptureController.frames() > 20) CaptureController.requestStop();
            var path = CaptureController.lastReport();
            if (captureStarted && !CaptureController.isCapturing() && !CaptureController.isExporting() && path != null && !path.equals(previous)) {
                try (var zip = new ZipFile(path.toFile()); var reader = new InputStreamReader(zip.getInputStream(zip.getEntry("report.json")), StandardCharsets.UTF_8)) {
                    JsonObject report = JsonParser.parseReader(reader).getAsJsonObject();
                    var metrics = report.getAsJsonObject("metrics");
                    if (metrics.getAsJsonObject("summary").get("frameCount").getAsInt() < 3) throw new IllegalStateException("too few frames");
                    if (metrics.get("phaseMismatches").getAsLong() != 0) throw new IllegalStateException("unbalanced phases");
                    var phases = new HashSet<String>();
                    for (var p : metrics.getAsJsonArray("phases")) phases.add(p.getAsJsonObject().get("name").getAsString());
                    if (!phases.contains("render") || !phases.contains("frame_swap") || !phases.contains("vertex_upload")) throw new IllegalStateException("hooks not observed: " + phases);
                    if (report.getAsJsonObject("samples").get("recordedSamples").getAsLong() < 1) throw new IllegalStateException("no samples");
                    if (!report.getAsJsonObject("environment").has("graphics")) throw new IllegalStateException("no graphics metadata");
                    for (var item : report.getAsJsonObject("samples").getAsJsonArray("mods")) {
                        String id = item.getAsJsonObject().get("mod").getAsString();
                        if (id.equals("mixinextras") || id.equals("sre_performance") || id.startsWith("fabric-")) throw new IllegalStateException("infrastructure incorrectly attributed: " + id);
                    }
                    previous = path; reports++;
                    if (reports == 1) {
                        if (!CaptureController.start(10, 50)) throw new IllegalStateException("second start failed");
                    } else {
                        LoggerFactory.getLogger("sre-performance-smoke").info("SRE_PERF_SMOKE_PASS reports={} phases={} file={}", reports, phases, path);
                        client.stop();
                    }
                } catch (Exception e) { throw new IllegalStateException("SRE_PERF_SMOKE_FAILED", e); }
            }
            if (CaptureController.exportFailure() != null) throw new IllegalStateException("SRE_PERF_SMOKE_EXPORT_FAILED: " + CaptureController.exportFailure());
        });
    }
}

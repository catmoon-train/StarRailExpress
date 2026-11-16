package io.wifi.sreperf.memory;

import net.minecraft.network.chat.Component;

import java.util.Map;

/** 服务端控制台和未安装本模组的客户端也能看到可读的英文回退消息。 */
public final class MemoryMessages {
    static final Map<String, String> FALLBACKS = Map.ofEntries(
            Map.entry("sreperf.memory.server_help", "Server memory diagnostics (permission 2): /sreperfserver memory [histogram | allocation [seconds 10–180]]. Reports stay on the server. /sreperf memory is LOCAL on clients with this mod."),
            Map.entry("sreperf.memory.started", "Local JVM memory snapshot queued; loaded-world counts collected without loading chunks."),
            Map.entry("sreperf.memory.busy", "A memory diagnostic is already running in this JVM. Please wait."),
            Map.entry("sreperf.memory.histogram_warning", "Explicit histogram requested: a safepoint heap scan may briefly PAUSE the game/server. Using gcClassHistogram -all without requesting GC; shallow bytes are NOT mod retained heap."),
            Map.entry("sreperf.memory.allocation_started", "JFR allocation sampling queued for %s seconds (32 MiB rolling cap). Allocations are NOT retained memory. Unsupported runtimes will report a reason."),
            Map.entry("sreperf.memory.saved", "Memory report saved locally: %s (report.txt / report.json; not a frame-report ZIP)."),
            Map.entry("sreperf.memory.unsupported", "Requested diagnostic is unavailable or failed; reason is included in the report: %s"),
            Map.entry("sreperf.memory.failed", "Memory diagnostics failed: %s. Check logs, disk space and permissions; partial files may remain."),
            Map.entry("sreperf.memory.cancelled", "Memory diagnostic cancelled during shutdown; an incomplete report directory may remain.")
    );
    private MemoryMessages() { }

    public static Component component(String key, Object... args) {
        return Component.translatableWithFallback(key, FALLBACKS.getOrDefault(key, key), args);
    }
}

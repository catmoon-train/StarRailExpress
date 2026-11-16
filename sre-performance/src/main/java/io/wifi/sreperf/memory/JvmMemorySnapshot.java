package io.wifi.sreperf.memory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按需读取管理接口；不触发 GC、不遍历堆，也不安装常驻采样器。 */
public final class JvmMemorySnapshot {
    private JvmMemorySnapshot() { }

    public static Map<String, Object> capture() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> unavailable = new ArrayList<>();
        out.put("capturedAt", Instant.now().toString());
        out.put("heap", null);
        out.put("nonHeap", null);
        out.put("threads", null);
        try {
            var memory = ManagementFactory.getMemoryMXBean();
            out.put("heap", usage(memory.getHeapMemoryUsage()));
            out.put("nonHeap", usage(memory.getNonHeapMemoryUsage()));
        } catch (RuntimeException | LinkageError e) { unavailable.add("memory: " + e); }
        List<Object> pools = new ArrayList<>();
        try {
            for (var pool : ManagementFactory.getMemoryPoolMXBeans()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", pool.getName());
                row.put("type", pool.getType().toString());
                row.put("usage", usage(pool.getUsage()));
                row.put("peakUsage", usage(pool.getPeakUsage()));
                pools.add(row);
            }
        } catch (RuntimeException | LinkageError e) { unavailable.add("pools: " + e); }
        out.put("memoryPools", pools);
        List<Object> buffers = new ArrayList<>();
        try {
            for (var buffer : ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean.class)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", buffer.getName());
                row.put("count", known(buffer.getCount()));
                row.put("memoryUsedBytes", known(buffer.getMemoryUsed()));
                row.put("totalCapacityBytes", known(buffer.getTotalCapacity()));
                buffers.add(row);
            }
        } catch (RuntimeException | LinkageError e) { unavailable.add("buffers: " + e); }
        out.put("bufferPools", buffers);
        List<Object> collectors = new ArrayList<>();
        try {
            for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", gc.getName());
                row.put("collectionCount", known(gc.getCollectionCount()));
                row.put("collectionTimeMs", known(gc.getCollectionTime()));
                collectors.add(row);
            }
        } catch (RuntimeException | LinkageError e) { unavailable.add("gc: " + e); }
        out.put("gcCumulativeSinceJvmStart", collectors);
        try {
            var threads = ManagementFactory.getThreadMXBean();
            out.put("threads", Map.of("live", threads.getThreadCount(), "daemon", threads.getDaemonThreadCount(),
                    "peak", threads.getPeakThreadCount(), "totalStarted", threads.getTotalStartedThreadCount()));
        } catch (RuntimeException | LinkageError e) { unavailable.add("threads: " + e); }
        out.put("osAndProcess", os());
        out.put("unavailable", unavailable);
        out.put("measurement", "MXBean point observations, bytes unless named otherwise; not atomic. GC counters are JVM-lifetime totals; null means unavailable, never zero. Buffers are JVM-tracked, not all native memory.");
        return out;
    }

    static Map<String, Object> usage(MemoryUsage usage) {
        if (usage == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("usedBytes", known(usage.getUsed()));
        out.put("committedBytes", known(usage.getCommitted()));
        out.put("maxBytes", known(usage.getMax()));
        out.put("initBytes", known(usage.getInit()));
        return out;
    }

    static Long known(long value) { return value < 0 ? null : value; }

    private static Map<String, Object> os() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        try {
            var server = ManagementFactory.getPlatformMBeanServer();
            var name = new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
            out.put("processCpuTimeNs", attribute(server, name, "ProcessCpuTime", "Cumulative CPU time of all JVM process threads, nanoseconds; not elapsed time."));
            out.put("processCpuLoad", attribute(server, name, "ProcessCpuLoad", "Recent JVM process CPU load [0,1], normalized over available CPUs; vendor-defined interval, not this command's interval."));
            out.put("systemCpuLoad", attribute(server, name, "CpuLoad", "Recent system CPU load [0,1]; OS/container scope and interval depend on JVM implementation."));
            out.put("committedVirtualMemoryBytes", attribute(server, name, "CommittedVirtualMemorySize", "Process committed virtual memory bytes, NOT resident physical memory (RSS)."));
            out.put("systemTotalMemoryBytes", attribute(server, name, "TotalMemorySize", "OS/container physical memory bytes exposed by JVM; not JVM heap."));
            out.put("systemFreeMemoryBytes", attribute(server, name, "FreeMemorySize", "OS/container free physical memory bytes exposed by JVM."));
        } catch (Exception | LinkageError e) { out.put("managementUnavailable", e.toString()); }
        Map<String, Object> rss = new LinkedHashMap<>();
        rss.put("value", null);
        rss.put("source", "/proc/self/status VmRSS × 1024; current process resident pages including heap/native/shared mappings; not heap retained size, not GPU VRAM.");
        try {
            if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux")) {
                throw new UnsupportedOperationException("No portable RSS API on this OS; virtual committed memory is not substituted.");
            }
            try (var lines = Files.lines(Path.of("/proc/self/status"))) {
                String line = lines.filter(s -> s.startsWith("VmRSS:")).findFirst().orElseThrow();
                rss.put("value", parseLinuxRss(line));
            }
        } catch (Exception e) { rss.put("unavailable", e.toString()); }
        out.put("residentSetBytes", rss);
        return out;
    }

    static long parseLinuxRss(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 3 || !parts[0].equals("VmRSS:") || !parts[2].equals("kB")) {
            throw new IllegalArgumentException("Unsupported VmRSS format");
        }
        long bytes = Math.multiplyExact(Long.parseLong(parts[1]), 1024L);
        if (bytes < 0) throw new IllegalArgumentException("Negative RSS");
        return bytes;
    }

    private static Map<String, Object> attribute(MBeanServer server, ObjectName name, String attribute, String scope) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("value", null);
        out.put("source", "java.lang:type=OperatingSystem / " + attribute);
        out.put("measurement", scope);
        try {
            Object value = server.getAttribute(name, attribute);
            if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < 0) {
                throw new UnsupportedOperationException("Interface returned an unavailable value");
            }
            out.put("value", value);
        } catch (Exception | LinkageError e) { out.put("unavailable", e.toString()); }
        return out;
    }
}

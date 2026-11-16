package io.wifi.sreperf.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 内存报告与帧报告分离；可读文本直接保留全部字段，不输出可执行 HTML。 */
public final class MemoryReportWriter {
    private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    public static final String LIMITS = """
            内存诊断 / Memory diagnostics — 统计口径与限制
            Java 堆 used/committed/max、non-heap、direct/mapped buffer、进程 RSS 与显存是不同指标，不能直接相加。
            Java heap, NativeImage 原生像素缓冲和 GPU 显存不同；本工具不测完整 NativeImage/native 分配或 GPU VRAM。
            快照只读本 JVM 管理接口；客户端不读取远端服务器。单人游戏的集成服务器与客户端共享 JVM 堆。
            实体按注册 ID 统计已加载索引，区块只读已加载缓存计数，不会加载新区块。不包括 PC/队伍或未加载存档。
            cobblemon / mega_showdown 实体数量并不等于这些模组的内存；共享模型、贴图、缓存和数组不能按数量摊分。
            histogram 的 shallowBytes 是对象本体大小（含不可达待回收对象），不是 retained heap，不证明某模组独占保留量。
            JFR allocation samples 是采样分配压力，weight 为估算分配字节，不是现存/保留内存，也不是精确逐对象计数。
            不能仅凭对象数量、类名或线程栈采样归因真实 retained heap；需要引用链/支配树及受控对照才能判断。
            本工具不主动调用 System.gc、不默认生成堆转储；手动 histogram 仍可能导致短暂停顿。
            null/unavailable 表示接口未测量或不支持，不是零。GC 为 JVM 启动以来累计值，不是此次命令的暂停。
            CPU load 由 JVM 定义近期采样窗口，RSS 仅在可读取 /proc/self/status 时测量；虚拟提交量绝不冒充 RSS。
            报告包含类名/栈、模组实体 ID、维度 ID；不读取实体 NBT、位置、玩家身份或网络地址。分享前请检查。
            English: counts, shallow size and allocation samples do NOT establish mod retained heap.
            Heap / native image buffers / GPU VRAM are distinct. JVM metrics are process-wide, not remote-server metrics.
            """;

    private MemoryReportWriter() { }

    public static Path createDirectory(Path base, String kind) throws IOException {
        if (!kind.matches("[a-z]+")) throw new IllegalArgumentException("Invalid report kind");
        Files.createDirectories(base);
        return Files.createTempDirectory(base, "memory-" + kind + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + "-");
    }

    public static Path write(Path directory, Map<String, Object> report, String histogram) throws IOException {
        String json = GSON.toJson(report);
        Files.writeString(directory.resolve("report.json"), json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(directory.resolve("report.txt"), summary(report) + "\n" + LIMITS + "\n完整字段 / Full measurements (bytes unless named otherwise):\n" + json,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        if (histogram != null) Files.writeString(directory.resolve("histogram.txt"), histogram,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return directory;
    }

    private static String summary(Map<String, Object> report) {
        StringBuilder text = new StringBuilder("SRE 内存报告 / Memory report\n");
        text.append("Side: ").append(report.get("side")).append(" | Kind: ").append(report.get("kind")).append('\n');
        if (report.get("jvm") instanceof Map<?, ?> jvm) {
            for (String area : java.util.List.of("heap", "nonHeap")) {
                text.append(area).append(": ");
                if (jvm.get(area) instanceof Map<?, ?> usage) {
                    text.append("used ").append(bytes(usage.get("usedBytes")))
                            .append(" | committed ").append(bytes(usage.get("committedBytes")))
                            .append(" | max ").append(bytes(usage.get("maxBytes")));
                } else text.append("未测量 / unavailable");
                text.append('\n');
            }
        }
        if (report.get("loadedWorld") instanceof Map<?, ?> loaded && loaded.get("worlds") instanceof Iterable<?> worlds) {
            text.append("已加载世界 / Loaded worlds (counts, NOT bytes):\n");
            for (Object value : worlds) if (value instanceof Map<?, ?> world) {
                text.append("  ").append(world.get("dimension")).append(" | chunks ").append(world.get("loadedChunks"))
                        .append(" | entities ").append(world.get("loadedEntities")).append('\n');
                text.append("  namespaces: ").append(world.get("entitiesByNamespace")).append('\n');
            }
        }
        for (String section : java.util.List.of("histogram", "allocation")) {
            if (!(report.get(section) instanceof Map<?, ?> data)) continue;
            text.append(section.equals("histogram") ? "类对象本体 / Class shallow size (NOT retained):\n" : "采样分配 / Sampled allocation (NOT retained):\n");
            if (Boolean.FALSE.equals(data.get("supported"))) text.append("  unavailable: ").append(data.get("reason")).append('\n');
            if (data.get("classes") instanceof Iterable<?> classes) {
                int rows = 0;
                for (Object value : classes) {
                    if (++rows > 30) { text.append("  ... 完整列表见下方字段 / full list below\n"); break; }
                    if (!(value instanceof Map<?, ?> row)) continue;
                    text.append("  ").append(row.get("class")).append(" | ");
                    if (section.equals("histogram")) text.append(row.get("instances")).append(" objects | shallow ").append(bytes(row.get("shallowBytes")));
                    else text.append(row.get("samples")).append(" samples | weighted allocation ").append(bytes(row.get("sampleWeightBytes")));
                    text.append('\n');
                }
            }
        }
        return text.toString();
    }

    private static String bytes(Object value) {
        return value instanceof Number number ? String.format(java.util.Locale.ROOT, "%d B (%.2f MiB)", number.longValue(), number.doubleValue() / 1048576.0)
                : "未测量 / unavailable";
    }
}

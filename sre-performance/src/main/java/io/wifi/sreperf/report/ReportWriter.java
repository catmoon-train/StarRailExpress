package io.wifi.sreperf.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 将调用方提供的安全元数据导出到本地；不自行读取系统、玩家或网络信息。 */
public final class ReportWriter {
    private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private static final List<String> FILES = List.of("report.html", "report.json", "frames.csv", "README.txt");
    private static final List<String> FRAME_FIELDS = List.of(
            "index", "startOffsetMs", "intervalMs", "workMs", "focused", "inWorld", "screenOpen");
    private static final String LIMITS = """
            帧间隔是相邻 frameStart 的差，第一帧没有间隔；work 是 frameEnd - frameStart，不是帧间隔。
            平均 FPS = 有效间隔数 × 1e9 / 间隔总纳秒；P50/P95/P99/max 均为帧间隔，分位数使用 nearest-rank。
            over50Ms / over100Ms 是严格超过 50 / 100 毫秒的间隔数。无数据使用 null，CSV 留空，不把缺失解释为零。
            纯前台世界仅纳入相邻两帧都 focused && inWorld && !screenOpen 的间隔；孤立合格帧仍计入该组 frameCount。
            阶段是主线程墙钟耗时，包含驱动、同步和其他等待；上传耗时不是 GPU 执行时间，也不代表 GPU 利用率。
            totalMs 是各次调用包含子阶段的耗时之和，同名递归也计为多次调用；selfMs 排除直接子作用域。
            不得把不同阶段或递归的 inclusive 耗时相加当作帧耗时。逐帧 phase 列是该名称区间的并集，同名递归不重复计时。
            线程栈的模组归属不是独占耗时，可能重叠或来自注入；不可相加推算删除模组的收益，采样比例不等于 FPS 收益。
            采样存在误差和自身开销；请同时查看 overheadMs、errors、droppedSamples 及计时异常计数。未采集或不支持不等于零开销。
            乱序退出和漏关作用域会被截断并计入 phaseMismatches；倒退时钟会被钳制，同时计入 timingAnomalies。
            depthOverflows / phaseOverflows 表示作用域栈或名称容量不足；根 FRAME 占用 64 层和 32 个名称中的一项。
            只保存已结束的帧；incompleteFrame=true 表示停止前未结束的帧被排除。上下文只在帧起点观察，不能证明整段场景完全相同。
            GC 统计和线程采样不能单独证明长帧根因；请在相同场景、前后台状态和配置下对照。
            """;

    private ReportWriter() {
    }

    /** 成功关闭 ZIP 后才返回路径；独占创建目录和 ZIP，失败会清理本次创建的文件。 */
    public static Path write(Path baseDir, Map<String, Object> report) throws IOException {
        Objects.requireNonNull(baseDir, "baseDir");
        Objects.requireNonNull(report, "report");
        JsonObject data = GSON.toJsonTree(report).getAsJsonObject();
        Files.createDirectories(baseDir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        Path directory = Files.createTempDirectory(baseDir, "sre-performance-" + stamp + "-");
        Path zip = directory.resolveSibling(directory.getFileName() + ".zip");
        boolean ownsZip = false;
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(directory.resolve("report.json"),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                GSON.toJson(data, writer);
            }
            Files.writeString(directory.resolve("report.html"), html(data), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            csv(directory.resolve("frames.csv"), object(data.get("metrics")));
            Files.writeString(directory.resolve("README.txt"), """
                    SRE 客户端性能诊断报告（UTF-8）
                    双击 report.html 可完全离线查看；report.json 保留结构化原始数据，frames.csv 用于逐帧分析。
                    分享时发送同名 ZIP 即可，内含这四份文件；无需上传整个游戏目录或日志。
                    导出器不额外采集隐私或凭据；分享前仍请检查自行填写的标记和环境元数据。

                    统计口径与限制：
                    """ + LIMITS, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            try (OutputStream stream = Files.newOutputStream(zip, StandardOpenOption.CREATE_NEW)) {
                ownsZip = true;
                try (ZipOutputStream archive = new ZipOutputStream(stream, StandardCharsets.UTF_8)) {
                    for (String name : FILES) {
                        archive.putNextEntry(new ZipEntry(name));
                        Files.copy(directory.resolve(name), archive);
                        archive.closeEntry();
                    }
                }
            }
            return zip;
        } catch (IOException | RuntimeException failure) {
            if (ownsZip) {
                cleanup(zip, failure);
            }
            for (String name : FILES) {
                cleanup(directory.resolve(name), failure);
            }
            cleanup(directory, failure);
            throw failure;
        }
    }

    private static void cleanup(Path path, Exception failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static String html(JsonObject data) {
        JsonObject metrics = object(data.get("metrics"));
        JsonObject samples = object(data.get("samples"));
        StringBuilder out = new StringBuilder(8192);
        out.append("""
                <!doctype html><html lang="zh-CN"><head><meta charset="UTF-8">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>SRE 客户端性能诊断报告</title><style>
                body{font:15px/1.6 system-ui,sans-serif;max-width:1200px;margin:2em auto;padding:0 1em;color:#222}
                table{border-collapse:collapse;display:block;overflow-x:auto}th,td{border:1px solid #ccc;padding:.3em .6em;text-align:left}
                pre{white-space:pre-wrap;overflow-wrap:anywhere}dd,td{overflow-wrap:anywhere}dt{font-weight:bold}h2{margin-top:1.8em}
                </style></head><body><h1>SRE 客户端性能诊断报告</h1><p>本报告完全离线，无外部请求。格式版本：
                """);
        value(out, data.get("schemaVersion"));
        out.append("</p><h2>摘要：全部帧</h2>");
        summary(out, object(metrics.get("summary")));
        out.append("<h2>摘要：纯前台世界（无界面）</h2>");
        summary(out, object(metrics.get("foregroundWorld")));
        out.append("<h2>主线程阶段</h2><p>墙钟耗时含等待；上传不是 GPU 执行时间。包含时间不能跨阶段相加。</p>");
        table(out, metrics.get("phases"), List.of("name", "calls", "totalMs", "selfMs", "maxMs", "bytes"));
        JsonObject anomalies = new JsonObject();
        for (String key : List.of("phaseMismatches", "depthOverflows", "phaseOverflows", "timingAnomalies", "incompleteFrame")) {
            anomalies.add(key, metrics.get(key));
        }
        section(out, "计时异常与容量", anomalies);
        out.append("<h2>线程栈采样</h2><p>模组归属不是独占耗时，不可相加推算删模组收益。</p><dl>");
        item(out, "采样自身开销 overheadMs（毫秒）", samples.get("overheadMs"));
        item(out, "采样错误 errors", samples.get("errors"));
        item(out, "丢弃采样 droppedSamples", samples.get("droppedSamples"));
        out.append("</dl>");
        // 保留采样器未来补充的状态、支持性说明和其他有界字段。
        JsonObject extraSamples = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : samples.entrySet()) {
            if (!Set.of("threads", "mods", "hotspots", "overheadMs", "errors", "droppedSamples").contains(entry.getKey())) {
                extraSamples.add(entry.getKey(), entry.getValue());
            }
        }
        section(out, "采样状态", extraSamples);
        section(out, "采样模组归属（非独占）", samples.get("mods"));
        section(out, "采样热点", samples.get("hotspots"));
        section(out, "采样线程", samples.get("threads"));
        section(out, "GC 与内存（不支持的项目不会假定为零）", data.get("gc"));
        section(out, "安全环境元数据", data.get("environment"));
        section(out, "用户标记", data.get("marks"));
        section(out, "警告", data.get("warnings"));
        out.append("<h2>口径与限制</h2><pre>").append(escape(LIMITS)).append("</pre></body></html>");
        return out.toString();
    }

    private static void summary(StringBuilder out, JsonObject data) {
        if (data.isEmpty()) {
            value(out, JsonNull.INSTANCE);
            return;
        }
        out.append("<table><thead><tr><th>指标</th><th>值</th></tr></thead><tbody>");
        String[][] labels = {
                {"frameCount", "帧数"}, {"intervalCount", "有效帧间隔数"}, {"averageFps", "平均 FPS"},
                {"p50Ms", "P50 间隔（毫秒）"}, {"p95Ms", "P95 间隔（毫秒）"}, {"p99Ms", "P99 间隔（毫秒）"},
                {"maxMs", "最大间隔（毫秒）"}, {"over50Ms", "超过 50 毫秒的间隔数"},
                {"over100Ms", "超过 100 毫秒的间隔数"}, {"totalIntervalMs", "间隔总时长（毫秒）"}};
        for (String[] label : labels) {
            out.append("<tr><th>").append(label[1]).append("</th><td>");
            value(out, data.get(label[0]));
            out.append("</td></tr>");
        }
        out.append("</tbody></table>");
    }

    private static void section(StringBuilder out, String title, JsonElement data) {
        out.append("<h2>").append(escape(title)).append("</h2>");
        value(out, data);
    }

    private static void item(StringBuilder out, String key, JsonElement data) {
        out.append("<dt>").append(escape(key)).append("</dt><dd>");
        value(out, data);
        out.append("</dd>");
    }

    private static void value(StringBuilder out, JsonElement data) {
        if (data == null || data.isJsonNull()
                || (data.isJsonArray() && data.getAsJsonArray().isEmpty())
                || (data.isJsonObject() && data.getAsJsonObject().isEmpty())) {
            out.append("<span>无数据（未采集或不支持，详见警告）</span>");
        } else if (data.isJsonObject()) {
            JsonObject obj = data.getAsJsonObject();
            if (obj.has("supported") && obj.get("supported").isJsonPrimitive()
                    && "false".equals(obj.get("supported").getAsString())) {
                out.append("<p>当前环境不支持此项。</p>");
            }
            out.append("<dl>");
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                item(out, entry.getKey(), entry.getValue());
            }
            out.append("</dl>");
        } else if (data.isJsonArray()) {
            out.append("<ol>");
            for (JsonElement entry : data.getAsJsonArray()) {
                out.append("<li>");
                value(out, entry);
                out.append("</li>");
            }
            out.append("</ol>");
        } else {
            out.append(escape(data.getAsString()));
        }
    }

    private static void table(StringBuilder out, JsonElement data, List<String> fields) {
        JsonArray rows = array(data);
        if (rows.isEmpty()) {
            value(out, JsonNull.INSTANCE);
            return;
        }
        out.append("<table><thead><tr>");
        for (String field : fields) {
            out.append("<th>").append(escape(field)).append("</th>");
        }
        out.append("</tr></thead><tbody>");
        for (JsonElement row : rows) {
            out.append("<tr>");
            for (String field : fields) {
                out.append("<td>");
                value(out, object(row).get(field));
                out.append("</td>");
            }
            out.append("</tr>");
        }
        out.append("</tbody></table>");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static void csv(Path path, JsonObject metrics) throws IOException {
        Set<String> phases = new LinkedHashSet<>();
        for (JsonElement phase : array(metrics.get("phases"))) {
            JsonElement name = object(phase).get("name");
            if (name != null && name.isJsonPrimitive()) {
                phases.add(name.getAsString());
            }
        }
        JsonArray frames = array(metrics.get("frames"));
        for (JsonElement frame : frames) {
            phases.addAll(object(object(frame).get("phasesMs")).keySet());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW)) {
            List<String> header = new ArrayList<>(FRAME_FIELDS);
            for (String phase : phases) {
                header.add("phase." + phase + ".ms");
            }
            csvRow(writer, header);
            for (JsonElement element : frames) {
                JsonObject frame = object(element);
                List<String> row = new ArrayList<>(header.size());
                for (String field : FRAME_FIELDS) {
                    row.add(csvText(frame.get(field)));
                }
                JsonObject times = object(frame.get("phasesMs"));
                for (String phase : phases) {
                    row.add(csvText(times.get(phase)));
                }
                csvRow(writer, row);
            }
        }
    }

    private static String csvText(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "";
        }
        return value.isJsonPrimitive() ? value.getAsString() : GSON.toJson(value);
    }

    private static void csvRow(BufferedWriter writer, List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            String cell = cells.get(i);
            if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
                writer.write('"');
                writer.write(cell.replace("\"", "\"\""));
                writer.write('"');
            } else {
                writer.write(cell);
            }
        }
        writer.write("\r\n");
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonElement value) {
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
}

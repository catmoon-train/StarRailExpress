package io.wifi.sreperf.report;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.sreperf.core.SessionMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class ReportWriterTest {
    private static final String MARK = "<script>\"&'中文</script>";
    private static final String PHASE = "上传,\"一\"\r\n<阶段>&'";
    private static final Set<String> EXPORTS = Set.of("report.html", "report.json", "frames.csv", "README.txt");

    @TempDir
    Path temp;

    @Test
    void exportsUtf8NullsAndExactlyFourZipEntriesWithIdenticalBytes() throws IOException {
        Path zip = ReportWriter.write(temp, populatedReport());
        Path directory = directory(zip);
        assertEquals(temp, zip.getParent());
        assertTrue(Files.isDirectory(directory));
        assertTrue(directory.getFileName().toString().matches("sre-performance-\\d{8}-\\d{6}-\\d{3}-.+"));
        try (var files = Files.list(directory)) {
            assertEquals(EXPORTS, files.map(path -> path.getFileName().toString()).collect(Collectors.toSet()));
        }
        try (ZipFile archive = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            assertEquals(EXPORTS, archive.stream().map(ZipEntry::getName).collect(Collectors.toSet()));
            assertEquals(4, archive.size());
            for (String name : EXPORTS) {
                assertFalse(archive.getEntry(name).isDirectory());
                try (var stream = archive.getInputStream(archive.getEntry(name))) {
                    assertArrayEquals(Files.readAllBytes(directory.resolve(name)), stream.readAllBytes());
                }
            }
        }
        String json = Files.readString(directory.resolve("report.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("中文"));
        JsonObject data = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(MARK, data.getAsJsonArray("marks").get(0).getAsJsonObject().get("label").getAsString());
        assertTrue(data.getAsJsonObject("metrics").getAsJsonArray("frames").get(0)
                .getAsJsonObject().get("intervalMs").isJsonNull());
        assertTrue(data.getAsJsonObject("environment").has("optional"));
        assertTrue(data.getAsJsonObject("environment").get("optional").isJsonNull());
        assertTrue(Files.readString(directory.resolve("README.txt"), StandardCharsets.UTF_8).contains("发送同名 ZIP"));
    }

    @Test
    void htmlEscapesAllDynamicKeysAndValuesAndExplainsMeasurementLimits() throws IOException {
        Path zip = ReportWriter.write(temp, populatedReport());
        String html = Files.readString(directory(zip).resolve("report.html"), StandardCharsets.UTF_8);
        assertTrue(html.contains("&lt;script&gt;&quot;&amp;&#39;中文&lt;/script&gt;"));
        assertTrue(html.contains("&lt;b&gt;键&lt;/b&gt;"));
        assertTrue(html.contains("&lt;阶段&gt;&amp;&#39;"));
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<b>键</b>"));
        assertFalse(html.contains("<阶段>"));
        assertFalse(html.contains("<iframe"));
        assertFalse(html.contains("<link"));
        assertFalse(html.contains("src=\"http"));
        assertTrue(html.contains("Content-Security-Policy"));
        assertTrue(html.contains("default-src 'none'"));
        assertTrue(html.contains("主线程墙钟耗时"));
        assertTrue(html.contains("上传不是 GPU 执行时间"));
        assertTrue(html.contains("模组归属不是独占耗时"));
        assertTrue(html.contains("不可相加推算删模组收益"));
        assertTrue(html.contains("overheadMs"));
        assertTrue(html.contains("droppedSamples"));
        assertTrue(html.contains("phaseMismatches"));
        assertTrue(html.contains("采样热点"));
        assertTrue(html.contains("GC 与内存"));
        assertTrue(html.contains("安全环境元数据"));
        assertTrue(html.contains("当前环境不支持此项"));
    }

    @Test
    void csvQuotesCommasQuotesAndNewlinesAndLeavesMissingValuesEmpty() throws IOException {
        Path zip = ReportWriter.write(temp, populatedReport());
        String csv = Files.readString(directory(zip).resolve("frames.csv"), StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"phase.上传,\"\"一\"\"\r\n<阶段>&'.ms\""));
        List<List<String>> rows = parseCsv(csv);
        assertEquals(3, rows.size());
        List<String> header = rows.getFirst();
        assertEquals(List.of("index", "startOffsetMs", "intervalMs", "workMs", "focused", "inWorld", "screenOpen"),
                header.subList(0, 7));
        int phaseColumn = header.indexOf("phase." + PHASE + ".ms");
        assertTrue(phaseColumn >= 7);
        assertEquals(header.size(), rows.get(1).size());
        assertEquals(header.size(), rows.get(2).size());
        assertEquals("", rows.get(1).get(2));
        assertEquals("3.0", rows.get(1).get(3));
        assertEquals("1.0", rows.get(1).get(phaseColumn));
        assertEquals("10.0", rows.get(2).get(2));
        assertEquals("", rows.get(2).get(phaseColumn));
    }

    @Test
    void emptyReportsAndUnsupportedGcRemainExplicitWithoutInventedFps() throws IOException {
        Map<String, Object> report = emptyReport(new SessionMetrics(1).snapshot());
        Path zip = ReportWriter.write(temp, report);
        Path directory = directory(zip);
        String html = Files.readString(directory.resolve("report.html"), StandardCharsets.UTF_8);
        assertTrue(html.contains("无数据"));
        assertTrue(html.contains("不支持"));
        assertTrue(html.contains("未采集"));
        assertTrue(html.contains("纯前台世界"));
        JsonObject data = JsonParser.parseString(Files.readString(directory.resolve("report.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(data.getAsJsonObject("metrics").getAsJsonObject("summary").get("averageFps").isJsonNull());
        assertEquals(1, parseCsv(Files.readString(directory.resolve("frames.csv"), StandardCharsets.UTF_8)).size());
        // 缺少整个可选数据块也应能导出，而不是因强制类型转换而崩溃。
        Path minimal = ReportWriter.write(temp, Map.of());
        assertTrue(Files.readString(directory(minimal).resolve("report.html")).contains("无数据"));
    }

    @Test
    void repeatedExportsCreateUniqueSiblingsAndPreserveExistingFiles() throws IOException {
        Path existing = temp.resolve("keep.txt");
        Files.writeString(existing, "原文件", StandardCharsets.UTF_8);
        Map<String, Object> report = emptyReport(new SessionMetrics(1).snapshot());
        Path first = ReportWriter.write(temp, report);
        byte[] firstBytes = Files.readAllBytes(first);
        Path second = ReportWriter.write(temp, report);
        assertNotEquals(first, second);
        assertNotEquals(directory(first), directory(second));
        assertArrayEquals(firstBytes, Files.readAllBytes(first));
        assertEquals("原文件", Files.readString(existing, StandardCharsets.UTF_8));
        try (var files = Files.list(temp)) {
            List<Path> paths = files.toList();
            assertEquals(5, paths.size());
            assertEquals(2, paths.stream().filter(Files::isDirectory).count());
        }
    }

    @Test
    void invalidDestinationThrowsAndDoesNotOverwriteOrClaimSuccess() throws IOException {
        Path file = temp.resolve("not-a-directory");
        Files.writeString(file, "保留", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> ReportWriter.write(file, Map.of()));
        assertEquals("保留", Files.readString(file, StandardCharsets.UTF_8));
        try (var files = Files.list(temp)) {
            assertEquals(List.of(file), files.toList());
        }
    }

    private static Map<String, Object> populatedReport() {
        SessionMetrics metrics = new SessionMetrics(2);
        metrics.frameStart(0, true, true, false);
        long token = metrics.begin(PHASE, 1_000_000);
        metrics.end(PHASE, token, 2_000_000, 1024);
        metrics.frameEnd(3_000_000);
        metrics.frameStart(10_000_000, true, true, false);
        metrics.frameEnd(12_000_000);
        Map<String, Object> report = emptyReport(metrics.snapshot());
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("<b>键</b>", MARK);
        environment.put("optional", null);
        report.put("environment", environment);
        report.put("samples", Map.of("threads", List.of(Map.of("name", MARK)),
                "mods", List.of(Map.of("id", MARK)), "hotspots", List.of(Map.of("stack", List.of(MARK))),
                "overheadMs", 1.25, "errors", 2, "droppedSamples", 3, "supported", true));
        report.put("marks", List.of(Map.of("label", MARK, "offsetMs", 1.0)));
        report.put("warnings", List.of(MARK));
        return report;
    }

    private static Map<String, Object> emptyReport(Map<String, Object> metrics) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("environment", Map.of());
        report.put("metrics", metrics);
        Map<String, Object> samples = new LinkedHashMap<>();
        samples.put("threads", List.of());
        samples.put("mods", List.of());
        samples.put("hotspots", List.of());
        samples.put("overheadMs", null);
        samples.put("errors", 0);
        samples.put("droppedSamples", 0);
        report.put("samples", samples);
        report.put("gc", Map.of("supported", false, "reason", "未提供收集器通知"));
        report.put("marks", List.of());
        report.put("warnings", List.of());
        return report;
    }

    private static Path directory(Path zip) {
        String name = zip.getFileName().toString();
        return zip.resolveSibling(name.substring(0, name.length() - 4));
    }

    // 只用于断言 RFC 4180 引号规则，包括带换行的列名，不能直接按行或逗号分割。
    private static List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' && !quoted) {
                row.add(cell.toString());
                rows.add(row);
                row = new ArrayList<>();
                cell.setLength(0);
            } else if (c != '\r' || quoted) {
                cell.append(c);
            }
        }
        assertFalse(quoted, "unterminated quoted CSV cell");
        assertTrue(row.isEmpty() && cell.isEmpty(), "expected a final CRLF");
        return rows;
    }
}

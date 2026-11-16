package io.wifi.sreperf.memory;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemoryReportWriterTest {
    @TempDir Path temp;

    @Test void writesUtf8ReadableAndJsonWithNullsRawHistogramAndLimits() throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("heapMax", null);
        report.put("label", "中文内存");
        Path directory = MemoryReportWriter.createDirectory(temp, "histogram");
        assertEquals(directory, MemoryReportWriter.write(directory, report, ClassHistogramTest.TEXT));
        var json = JsonParser.parseString(Files.readString(directory.resolve("report.json"))).getAsJsonObject();
        assertTrue(json.get("heapMax").isJsonNull());
        assertEquals("中文内存", json.get("label").getAsString());
        String readable = Files.readString(directory.resolve("report.txt"));
        assertTrue(readable.contains("中文内存"));
        assertTrue(readable.contains("retained heap"));
        assertTrue(readable.contains("NativeImage"));
        assertTrue(readable.contains("GPU"));
        assertTrue(readable.contains("分配压力"));
        assertEquals(ClassHistogramTest.TEXT, Files.readString(directory.resolve("histogram.txt")));
    }

    @Test void uniqueDirectoriesAndCreateNewProtectExistingReports() throws Exception {
        Path first = MemoryReportWriter.createDirectory(temp, "snapshot");
        Path second = MemoryReportWriter.createDirectory(temp, "snapshot");
        assertNotEquals(first, second);
        MemoryReportWriter.write(first, Map.of("keep", true), null);
        String original = Files.readString(first.resolve("report.json"));
        assertThrows(java.io.IOException.class, () -> MemoryReportWriter.write(first, Map.of(), null));
        assertEquals(original, Files.readString(first.resolve("report.json")));
        assertFalse(Files.exists(first.resolve("histogram.txt")));
        assertThrows(IllegalArgumentException.class, () -> MemoryReportWriter.createDirectory(temp, "../escape"));
    }
}

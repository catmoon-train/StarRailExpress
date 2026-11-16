package io.wifi.sreperf.memory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryResourcesTest {
    @Test void translationsMatchAndServerFallbacksAreReadableWithoutClientInstall() throws Exception {
        JsonObject zh = json("assets/sre_performance/lang/zh_cn.json");
        JsonObject en = json("assets/sre_performance/lang/en_us.json");
        assertEquals(zh.keySet(), en.keySet());
        for (var fallback : MemoryMessages.FALLBACKS.entrySet()) {
            assertTrue(zh.has(fallback.getKey()));
            assertEquals(fallback.getValue(), en.get(fallback.getKey()).getAsString());
            assertEquals(fallback.getValue().split("%s", -1).length,
                    zh.get(fallback.getKey()).getAsString().split("%s", -1).length);
        }
    }

    @Test void commonEntrypointAndItsHelpersHaveNoClientClassReferences() throws Exception {
        JsonObject metadata = json("fabric.mod.json");
        assertEquals("*", metadata.get("environment").getAsString());
        assertEquals("io.wifi.sreperf.PerformanceCommon", metadata.getAsJsonObject("entrypoints").getAsJsonArray("main").get(0).getAsString());
        JsonObject mixins = json("sre-performance.mixins.json");
        assertFalse(mixins.has("mixins"));
        assertTrue(mixins.getAsJsonArray("client").size() > 0);
        for (String name : List.of("PerformanceCommon", "SourceResolver", "memory/MemoryDiagnostics", "memory/JvmMemorySnapshot",
                "memory/LoadedWorldSnapshot", "memory/ClassHistogram", "memory/MemoryMessages", "memory/MemoryReportWriter",
                "memory/JfrAllocation", "memory/AllocationSamples")) {
            String bytes = resource("io/wifi/sreperf/" + name + ".class", StandardCharsets.ISO_8859_1);
            for (String forbidden : List.of("net/minecraft/client/", "net/fabricmc/fabric/api/client/", "io/wifi/sreperf/PerformanceClient",
                    "io/wifi/sreperf/memory/MemoryClientCommands", "io/wifi/sreperf/CaptureController", "com/cobblemon/", "io/wifi/starrailexpress/")) {
                assertFalse(bytes.contains(forbidden), name + " references " + forbidden);
            }
        }
    }

    private static JsonObject json(String path) throws Exception { return JsonParser.parseString(resource(path, StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static String resource(String path, java.nio.charset.Charset charset) throws Exception {
        try (var stream = MemoryResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), charset);
        }
    }
}

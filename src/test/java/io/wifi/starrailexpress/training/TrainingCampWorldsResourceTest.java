package io.wifi.starrailexpress.training;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 只解析资源及 NBT；不调用 Bootstrap、不读取方块注册表、不创建服务端。 */
class TrainingCampWorldsResourceTest {
    @TempDir Path temporary;
    private static final String BASE = "/data/starrailexpress/";

    @Test
    void exactlyEightFixedVoidOverworldResourcesShareTheLocalLayout() throws Exception {
        var keys = new HashSet<Object>();
        for (int slot = 0; slot < TrainingCampPool.CAPACITY; slot++) {
            String name = "training_" + slot;
            try (var stream = getClass().getResourceAsStream(BASE + "dimension/" + name + ".json")) {
                assertNotNull(stream, name);
                JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals("minecraft:overworld", json.get("type").getAsString());
                var generator = json.getAsJsonObject("generator");
                assertEquals("minecraft:flat", generator.get("type").getAsString());
                var settings = generator.getAsJsonObject("settings");
                assertEquals("minecraft:plains", settings.get("biome").getAsString());
                assertFalse(settings.get("lakes").getAsBoolean());
                assertFalse(settings.get("features").getAsBoolean());
                assertEquals(0, settings.getAsJsonArray("structure_overrides").size());
                var layers = settings.getAsJsonArray("layers");
                assertEquals(1, layers.size());
                assertEquals("minecraft:air", layers.get(0).getAsJsonObject().get("block").getAsString());
                assertEquals(1, layers.get(0).getAsJsonObject().get("height").getAsInt());
            }
            var dimension = TrainingCampWorlds.dimension(slot);
            assertEquals("starrailexpress:" + name, dimension.location().toString());
            assertTrue(keys.add(dimension));
            assertEquals(dimension, TrainingCampWorlds.dimension(slot));
        }
        assertNull(getClass().getResourceAsStream(BASE + "dimension/training_8.json"));
        assertThrows(IllegalArgumentException.class, () -> TrainingCampWorlds.dimension(-1));
        assertThrows(IllegalArgumentException.class, () -> TrainingCampWorlds.dimension(8));
    }

    @Test
    void nativeAnvilPaletteDecoderPreservesAllEightyChunksAndSignNbt() throws Exception {
        byte[] archive = archive();
        var template = read(archive);
        assertEquals(1, template.version);
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive)), template.sha256);
        assertEquals(80, template.chunks.size());
        assertTrue(template.palette.size() > 20);
        assertEquals(65, template.spawn.getY());
        var chunks = new HashSet<String>();
        boolean foundLobbySign = false;
        long entities = 0;
        long beds = 0;
        for (var chunk : template.chunks) {
            assertTrue(chunks.add(chunk.x() + "," + chunk.z()));
            assertEquals(3, chunk.sections().size());
            for (var section : chunk.sections()) {
                assertEquals(4096, section.states().length);
                for (int state : section.states()) assertTrue(state >= 0 && state < template.palette.size());
            }
            entities += chunk.entities().size();
            for (CompoundTag entity : chunk.entities()) {
                if ("trainmurdermystery:trimmed_bed".equals(entity.getString("id"))) { beds++; continue; }
                assertEquals("minecraft:sign", entity.getString("id"));
                assertEquals(4, entity.getCompound("front_text").getList("messages", Tag.TAG_STRING).size());
                if (entity.getInt("x") == 19 && entity.getInt("z") == 108) {
                    assertTrue(entity.getCompound("front_text").getList("messages", Tag.TAG_STRING)
                            .getString(0).contains("列车整备训练站"));
                    foundLobbySign = true;
                }
            }
        }
        assertTrue(foundLobbySign);
        assertTrue(entities > 5);
        assertTrue(beds > 0);
        assertEquals(80L * 3 * 4096 + entities, template.cells);
        assertTrue(template.palette.stream().anyMatch(state -> state.getString("Name").equals("trainmurdermystery:metal_sheet_door")
                && state.getCompound("Properties").getString("open").equals("true")));
    }

    @Test
    void everyLayoutSpawnAndNavigationPointHasY65FloorAndClearHeadroom() throws Exception {
        var template = read(archive());
        try (var stream = getClass().getResourceAsStream(BASE + "training/layout.json")) {
            assertNotNull(stream);
            byte[] bytes = stream.readAllBytes();
            var layout = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            // Git 的 Windows 检出可转换换行符，ZIP 保持 LF；只要求布局语义完全相同。
            assertEquals(JsonParser.parseString(new String(streamEntry("layout.json"), StandardCharsets.UTF_8)), layout);
            for (var value : layout.getAsJsonArray("areas")) {
                var area = value.getAsJsonObject();
                clearPoint(template, area.getAsJsonArray("spawn"));
                for (String key : new String[]{"botSpawns", "patrol", "nodes"}) {
                    for (var point : area.getAsJsonArray(key)) clearPoint(template, point.getAsJsonArray());
                }
            }
        }
    }

    @Test
    void configurationsAreCopiedFromSameTemplateOnlyWhenMissing() throws Exception {
        var template = read(archive());
        Files.writeString(temporary.resolve("level.dat"), "ordinary world untouched");
        TrainingCampWorlds.ensureConfiguration(temporary, template);
        assertArrayEquals(streamEntry("train_maps/training.json"), Files.readAllBytes(temporary.resolve("train_maps/training.json")));
        assertArrayEquals(streamEntry("readyArea.json"), Files.readAllBytes(temporary.resolve("readyArea.json")));
        assertEquals("ordinary world untouched", Files.readString(temporary.resolve("level.dat")));
        assertFalse(Files.exists(temporary.resolve("region")));
        assertFalse(Files.exists(temporary.resolve("sre-training.json")));
        Files.writeString(temporary.resolve("train_maps/training.json"), "administrator configuration");
        Files.writeString(temporary.resolve("readyArea.json"), "existing ready area");
        TrainingCampWorlds.ensureConfiguration(temporary, template);
        assertEquals("administrator configuration", Files.readString(temporary.resolve("train_maps/training.json")));
        assertEquals("existing ready area", Files.readString(temporary.resolve("readyArea.json")));
    }

    @Test
    void completionMarkersAreBoundToSlotVersionAndTemplateHash() throws Exception {
        var template = read(archive());
        for (int slot = 0; slot < 8; slot++) {
            assertFalse(TrainingCampWorlds.markerMatches(temporary, slot, template));
            TrainingCampWorlds.writeMarker(temporary, slot, template);
            assertTrue(TrainingCampWorlds.markerMatches(temporary, slot, template));
        }
        Path marker = temporary.resolve("training-camps/slot_0.json");
        String original = Files.readString(marker);
        for (String bad : new String[]{"{}", "{", original.replace("\"slot\":0", "\"slot\":1"),
                original.replace("\"version\":1", "\"version\":2"), original.replace(template.sha256, "0".repeat(64))}) {
            Files.writeString(marker, bad);
            assertFalse(TrainingCampWorlds.markerMatches(temporary, 0, template));
        }
        TrainingCampWorlds.writeMarker(temporary, 0, template);
        assertTrue(TrainingCampWorlds.markerMatches(temporary, 0, template));
        try (var files = Files.list(marker.getParent())) { assertEquals(8, files.count()); }
    }

    @Test
    void refusesTruncatedZipTraversalExtraFilesAndSymlinkEntries() throws Exception {
        byte[] original = archive();
        assertThrows(IOException.class, () -> read(Arrays.copyOf(original, original.length - 22)));
        for (String path : new String[]{"../escaped", "/absolute", "region\\r.0.0.mca", "C:/escape", "unexpected"}) {
            var files = entries(original);
            files.put(path, new byte[]{1});
            assertThrows(IOException.class, () -> read(zip(files)), path);
        }
        byte[] symlink = original.clone();
        ByteBuffer buffer = ByteBuffer.wrap(symlink).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < symlink.length - 46; i++) {
            if (buffer.getInt(i) == 0x02014b50) {
                buffer.putInt(i + 38, 0120777 << 16);
                break;
            }
        }
        assertThrows(IOException.class, () -> read(symlink));
    }

    @Test
    void refusesOutOfRangeOverlappingAndMissingAnvilChunks() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            var files = entries(archive());
            byte[] region = files.get("region/r.0.0.mca");
            var buffer = ByteBuffer.wrap(region).order(ByteOrder.BIG_ENDIAN);
            if (mode == 0) buffer.putInt(0, 0x7fffff01);
            if (mode == 1) buffer.putInt(4, buffer.getInt(0));
            if (mode == 2) buffer.putInt(0, 0);
            assertThrows(IOException.class, () -> read(zip(files)));
        }
    }

    @Test
    void refusesFilesystemLinksRatherThanWritingOutsideWorld() throws Exception {
        var template = read(archive());
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path world = Files.createDirectory(temporary.resolve("world"));
        try { Files.createSymbolicLink(world.resolve("train_maps"), outside); }
        catch (IOException | UnsupportedOperationException unavailable) {
            assumeTrue(false, "Host does not permit symbolic links: " + unavailable.getMessage());
        }
        assertThrows(IOException.class, () -> TrainingCampWorlds.ensureConfiguration(world, template));
        assertFalse(Files.exists(outside.resolve("training.json")));
        Files.createSymbolicLink(world.resolve("training-camps"), outside);
        assertThrows(IOException.class, () -> TrainingCampWorlds.writeMarker(world, 0, template));
        assertFalse(TrainingCampWorlds.markerMatches(world, 0, template));
    }

    private static void clearPoint(TrainingCampWorlds.Template template, com.google.gson.JsonArray point) {
        int x = (int) Math.floor(point.get(0).getAsDouble());
        int y = (int) Math.floor(point.get(1).getAsDouble());
        int z = (int) Math.floor(point.get(2).getAsDouble());
        assertEquals(65, y);
        var chunk = template.chunks.stream().filter(data -> data.x() == (x >> 4) && data.z() == (z >> 4)).findFirst().orElseThrow();
        assertNotEquals("minecraft:air", template.palette.get(chunk.stateAt(x & 15, y - 1, z & 15)).getString("Name"));
        assertEquals("minecraft:air", template.palette.get(chunk.stateAt(x & 15, y, z & 15)).getString("Name"));
        assertEquals("minecraft:air", template.palette.get(chunk.stateAt(x & 15, y + 1, z & 15)).getString("Name"));
    }

    private byte[] archive() throws IOException {
        try (var stream = getClass().getResourceAsStream(BASE + "training/world-template.zip")) {
            assertNotNull(stream);
            return stream.readAllBytes();
        }
    }

    private byte[] streamEntry(String name) throws IOException { return entries(archive()).get(name); }
    private static TrainingCampWorlds.Template read(byte[] zip) throws IOException {
        return TrainingCampWorlds.Template.read(new ByteArrayInputStream(zip));
    }
    private static Map<String, byte[]> entries(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), zip.readAllBytes());
        }
        return entries;
    }
    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}

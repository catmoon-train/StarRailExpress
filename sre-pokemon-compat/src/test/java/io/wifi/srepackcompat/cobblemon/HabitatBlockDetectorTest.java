package io.wifi.srepackcompat.cobblemon;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 运行实际 handler 的字节码，只将 Minecraft 类型替换为不启动游戏的测试桩。 */
public class HabitatBlockDetectorTest {
    public static class Pos {
        final int x, z;
        Pos(int x, int z) { this.x = x; this.z = z; }
        public int getX() { return x; }
        public int getZ() { return z; }
    }
    public static class BlockEntity { }
    public enum Creation { CHECK }
    public static class Chunk {
        BlockEntity entity;
        Pos lastPos;
        public BlockEntity getBlockEntity(Pos pos, Creation creation) {
            assertEquals(Creation.CHECK, creation);
            lastPos = pos;
            return entity;
        }
    }
    public static class Source {
        Chunk chunk;
        int x, z, calls;
        public Chunk getChunkNow(int x, int z) {
            this.x = x; this.z = z; calls++;
            return chunk;
        }
    }
    public static class Level {
        final Source source = new Source();
        public Source getChunkSource() { return source; }
    }

    private Object read(Level level, Pos pos) throws Exception {
        String original = "io/wifi/srepackcompat/mixin/spawning/HabitatBlockDetectorMixin";
        var node = new ClassNode();
        try (var stream = getClass().getClassLoader().getResourceAsStream(original + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassRemapper(node, new SimpleRemapper(Map.of(
                    original, "io/wifi/srepackcompat/cobblemon/HabitatProbe",
                    "net/minecraft/core/BlockPos", Type.getInternalName(Pos.class),
                    "net/minecraft/server/level/ServerLevel", Type.getInternalName(Level.class),
                    "net/minecraft/server/level/ServerChunkCache", Type.getInternalName(Source.class),
                    "net/minecraft/world/level/chunk/LevelChunk", Type.getInternalName(Chunk.class),
                    "net/minecraft/world/level/chunk/LevelChunk$EntityCreationType", Type.getInternalName(Creation.class),
                    "net/minecraft/world/level/block/entity/BlockEntity", Type.getInternalName(BlockEntity.class)
            ))), 0);
        }
        var writer = new ClassWriter(0);
        node.accept(writer);
        class Loader extends ClassLoader {
            Loader() { super(HabitatBlockDetectorTest.class.getClassLoader()); }
            Class<?> probe() { var bytes = writer.toByteArray(); return defineClass(null, bytes, 0, bytes.length); }
        }
        var type = new Loader().probe();
        var method = type.getDeclaredMethod("sre$loadedHabitatOnly", Level.class, Pos.class);
        method.setAccessible(true);
        return method.invoke(type.getConstructor().newInstance(), level, pos);
    }

    @Test void unloadedChunkIsSkippedWithoutAnyLoadingApi() throws Exception {
        var level = new Level();
        assertNull(read(level, new Pos(128, 48)));
        assertEquals(1, level.source.calls);
        assertEquals(8, level.source.x);
        assertEquals(3, level.source.z);
    }

    @Test void loadedHabitatIsPreserved() throws Exception {
        var level = new Level();
        level.source.chunk = new Chunk();
        level.source.chunk.entity = new BlockEntity();
        var pos = new Pos(15, 16);
        assertSame(level.source.chunk.entity, read(level, pos));
        assertSame(pos, level.source.chunk.lastPos);
    }

    @Test void loadedChunkWithoutHabitatReturnsNull() throws Exception {
        var level = new Level();
        level.source.chunk = new Chunk();
        assertNull(read(level, new Pos(0, 0)));
    }

    @Test void negativeCoordinatesUseFloorChunkDivision() throws Exception {
        var level = new Level();
        read(level, new Pos(-1, -17));
        assertEquals(-1, level.source.x);
        assertEquals(-2, level.source.z);
    }

    @Test void chunkBecomingAvailableIsUsedOnNextDetection() throws Exception {
        var level = new Level();
        var pos = new Pos(32, 32);
        assertNull(read(level, pos));
        level.source.chunk = new Chunk();
        level.source.chunk.entity = new BlockEntity();
        assertSame(level.source.chunk.entity, read(level, pos));
        level.source.chunk = null;
        assertNull(read(level, pos));
    }

    @Test void patchIsRegisteredInCommonSpawningConfig() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("sre-pack-compat-spawning.mixins.json")) {
            assertNotNull(stream);
            var config = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(config.getAsJsonArray("mixins").asList().stream()
                    .anyMatch(entry -> entry.getAsString().equals("HabitatBlockDetectorMixin")));
            assertEquals("io.wifi.srepackcompat.cobblemon.SpawningMixinPlugin", config.get("plugin").getAsString());
        }
    }
}

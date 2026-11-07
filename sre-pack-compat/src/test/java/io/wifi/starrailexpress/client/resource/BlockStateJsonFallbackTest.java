package io.wifi.starrailexpress.client.resource;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BlockStateJsonFallbackTest {
    @TempDir Path root;

    PathPackResources pack(Path path, String name) {
        return new PathPackResources(new PackLocationInfo(name, net.minecraft.network.chat.Component.literal(name),
                PackSource.DEFAULT, Optional.empty()), path);
    }

    void write(Path base, String json) throws Exception {
        Path p = base.resolve("assets/test/blockstates/lantern.json");
        Files.createDirectories(p.getParent());
        Files.writeString(p, json);
    }

    @Test void thousandsOfStatesLoadOnlyDistinctBlocksInOrder() {
        var first = ResourceLocation.parse("amendments:wall_soul_lantern");
        var second = ResourceLocation.parse("supplementaries:rope");
        var states = new ArrayList<ModelResourceLocation>();
        for (int i = 0; i < 8192; i++) states.add(new ModelResourceLocation(first, "state=" + i));
        states.add(new ModelResourceLocation(second, "state=0"));
        states.add(new ModelResourceLocation(first, "state=8193"));
        assertEquals(List.of(first, second), List.copyOf(BlockStateJsonFallback.uniqueBlocks(states)));
        assertTrue(BlockStateJsonFallback.uniqueBlocks(List.of()).isEmpty());
    }

    @Test void readsEntireResourceStackPreservingPackOrder() throws Exception {
        Path lower = Files.createDirectory(root.resolve("lower"));
        Path higher = Files.createDirectory(root.resolve("higher"));
        write(lower, "{\"variants\":{\"\":{\"model\":\"test:block/low\"}}}");
        write(higher, "{\"variants\":{\"\":{\"model\":\"test:block/high\"}}}");
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                List.of(pack(lower, "lower"), pack(higher, "higher")))) {
            var json = BlockStateJsonFallback.load(resources, ResourceLocation.parse("test:blockstates/lantern.json"));
            assertEquals(List.of("lower", "higher"), json.stream().map(entry -> entry.source()).toList());
            assertEquals("test:block/high", json.get(1).data().getAsJsonObject().getAsJsonObject("variants")
                    .getAsJsonObject("").get("model").getAsString());
            assertEquals(2, json.size());
        }
    }

    @Test void missingDefinitionReturnsEmptyAndInvalidJsonDoesNotFakeSuccess() throws Exception {
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(root, "test")))) {
            assertTrue(BlockStateJsonFallback.load(resources, ResourceLocation.parse("test:blockstates/lantern.json")).isEmpty());
        }
        write(root, "{bad");
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(root, "test")))) {
            assertThrows(RuntimeException.class, () -> BlockStateJsonFallback.load(resources,
                    ResourceLocation.parse("test:blockstates/lantern.json")));
        }
    }
}

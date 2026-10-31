package io.wifi.starrailexpress.client.resource;

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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ModelJsonFallbackTest {
    @TempDir Path root;

    PathPackResources pack(Path path) {
        return new PathPackResources(new PackLocationInfo("test", net.minecraft.network.chat.Component.literal("test"),
                PackSource.DEFAULT, Optional.empty()), path);
    }

    Path write(Path base, String json) throws Exception {
        Path p = base.resolve("assets/test/models/item/example.json");
        Files.createDirectories(p.getParent());
        return Files.writeString(p, json);
    }

    @Test void readsParentAndTextureWithoutFullResourceScan() throws Exception {
        write(root, "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"test:item/example\"}}");
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(root)))) {
            var model = ModelJsonFallback.load(resources, ResourceLocation.parse("test:models/item/example.json"));
            assertNotNull(model);
            assertTrue(model.getDependencies().contains(ResourceLocation.parse("minecraft:item/generated")));
        }
    }

    @Test void respectsCurrentResourcePackPriority() throws Exception {
        Path lower = Files.createDirectory(root.resolve("low"));
        Path higher = Files.createDirectory(root.resolve("high"));
        write(lower, "{\"parent\":\"minecraft:item/generated\"}");
        write(higher, "{\"parent\":\"minecraft:item/handheld\"}");
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(lower), pack(higher)))) {
            var model = ModelJsonFallback.load(resources, ResourceLocation.parse("test:models/item/example.json"));
            assertTrue(model.getDependencies().contains(ResourceLocation.parse("minecraft:item/handheld")));
        }
    }

    @Test void doesNotInventMissingModels() throws Exception {
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(root)))) {
            assertNull(ModelJsonFallback.load(resources, ResourceLocation.parse("test:models/missing.json")));
        }
        assertNull(ModelJsonFallback.load(null, ResourceLocation.parse("test:models/missing.json")));
    }

    @Test void malformedJsonStillFailsRatherThanBecomingCachedSuccess() throws Exception {
        write(root, "{broken");
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(root)))) {
            assertThrows(RuntimeException.class, () -> ModelJsonFallback.load(resources,
                    ResourceLocation.parse("test:models/item/example.json")));
        }
    }
    @Test void cachedParentWrapperFallsBackToRawJson() throws Exception {
        write(root, "{\"parent\":\"minecraft:item/generated\"}");
        var wrapper = (net.minecraft.client.resources.model.UnbakedModel) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{net.minecraft.client.resources.model.UnbakedModel.class},
                (proxy, method, args) -> null);
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(root)))) {
            var getter = ModelJsonFallback.parents(resources, id -> wrapper);
            var parent = getter.apply(ResourceLocation.parse("test:item/example"));
            assertInstanceOf(net.minecraft.client.renderer.block.model.BlockModel.class, parent);
            assertSame(parent, getter.apply(ResourceLocation.parse("test:item/example")));
            assertSame(wrapper, getter.apply(ResourceLocation.parse("test:item/not_present")));
        }
    }

    @Test void ordinaryBlockModelParentIsNotReparsed() throws Exception {
        var original = net.minecraft.client.renderer.block.model.BlockModel.fromString("{}");
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(root)))) {
            assertSame(original, ModelJsonFallback.parents(resources, id -> original)
                    .apply(ResourceLocation.parse("test:item/example")));
        }
    }
}

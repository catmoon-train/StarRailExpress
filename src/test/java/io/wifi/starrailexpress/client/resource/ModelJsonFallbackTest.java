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

    private net.minecraft.client.resources.model.UnbakedModel cachedWrapper() {
        return (net.minecraft.client.resources.model.UnbakedModel) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{net.minecraft.client.resources.model.UnbakedModel.class},
                (proxy, method, args) -> null);
    }

    @Test void nestedCachedDefinitionIsReadAsBlockModel() throws Exception {
        write(root, "{\"parent\":\"minecraft:block/block\",\"textures\":{\"particle\":\"test:block/frame\"}}");
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(root)))) {
            var raw = ModelJsonFallback.definition(resources, ResourceLocation.parse("test:item/example"), cachedWrapper());
            assertInstanceOf(net.minecraft.client.renderer.block.model.BlockModel.class, raw);
            assertTrue(raw.getDependencies().contains(ResourceLocation.parse("minecraft:block/block")));
        }
    }

    @Test void nestedDefinitionRespectsResourcePackOverrides() throws Exception {
        Path low = Files.createDirectory(root.resolve("low"));
        Path high = Files.createDirectory(root.resolve("high"));
        write(low, "{\"parent\":\"minecraft:block/block\"}");
        write(high, "{\"parent\":\"minecraft:block/cube_all\"}");
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(low), pack(high)))) {
            var raw = ModelJsonFallback.definition(resources, ResourceLocation.parse("test:item/example"), cachedWrapper());
            assertTrue(raw.getDependencies().contains(ResourceLocation.parse("minecraft:block/cube_all")));
        }
    }

    @Test void coldDefinitionsAreNotReparsed() throws Exception {
        write(root, "{broken");
        var original = net.minecraft.client.renderer.block.model.BlockModel.fromString("{}");
        var wrapper = cachedWrapper();
        var id = ResourceLocation.parse("test:item/example");
        assertSame(wrapper, ModelJsonFallback.definition(null, id, wrapper));
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(root)))) {
            assertSame(original, ModelJsonFallback.definition(resources, id, original));
            assertNull(ModelJsonFallback.definition(resources, id, null));
        }
    }

    @Test void missingNestedDefinitionKeepsOriginalFailureSemantics() throws Exception {
        var wrapper = cachedWrapper();
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(root)))) {
            assertSame(wrapper, ModelJsonFallback.definition(resources,
                    ResourceLocation.parse("test:item/missing"), wrapper));
        }
    }

    @Test void malformedNestedDefinitionIsNotSilentlyReplaced() throws Exception {
        write(root, "{broken");
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(root)))) {
            assertThrows(RuntimeException.class, () -> ModelJsonFallback.definition(resources,
                    ResourceLocation.parse("test:item/example"), cachedWrapper()));
        }
    }

    @Test void nestedReadFailureKeepsOriginalIOException() {
        var cause = new java.io.IOException("test read failure");
        var resource = new net.minecraft.server.packs.resources.Resource(pack(root), () -> { throw cause; });
        var resources = (net.minecraft.server.packs.resources.ResourceManager) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{net.minecraft.server.packs.resources.ResourceManager.class},
                (proxy, method, args) -> Optional.of(resource));
        var error = assertThrows(java.io.UncheckedIOException.class, () -> ModelJsonFallback.definition(resources,
                ResourceLocation.parse("test:item/example"), cachedWrapper()));
        assertSame(cause, error.getCause());
    }
}

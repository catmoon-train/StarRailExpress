package io.wifi.starrailexpress.client.resource;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** 仅为 DashLoader 未缓存的模型按需读取 JSON，使用本轮资源管理器而不是全量重新扫描。 */
public final class ModelJsonFallback {
    private ModelJsonFallback() { }

    public static BlockModel load(ResourceManager manager, ResourceLocation file) throws IOException {
        if (manager == null) {
            return null;
        }
        var resource = manager.getResource(file);
        if (resource.isEmpty()) {
            return null;
        }
        try (var reader = resource.get().openAsReader()) {
            return BlockModel.fromStream(reader);
        }
    }

    /** 嵌套模型加载器需要可解析父模型的定义，而非已经烘焙好的缓存包装。 */
    public static UnbakedModel definition(ResourceManager resources, ResourceLocation id, UnbakedModel cached) {
        if (resources == null || cached == null || cached instanceof BlockModel) {
            return cached;
        }
        try {
            BlockModel raw = load(resources, id.withPath(path -> "models/" + path + ".json"));
            return raw == null ? cached : raw;
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot read nested model definition " + id, error);
        }
    }

    /** JSON parent 必须是 BlockModel，不能直接沿用 DashLoader 的已烘焙模型包装。 */
    public static Function<ResourceLocation, UnbakedModel> parents(ResourceManager resources,
                                                                 Function<ResourceLocation, UnbakedModel> delegate) {
        Map<ResourceLocation, BlockModel> parsed = new HashMap<>();
        return id -> {
            UnbakedModel model = delegate.apply(id);
            if (model instanceof BlockModel || resources == null) {
                return model;
            }
            if (!parsed.containsKey(id)) {
                try {
                    parsed.put(id, load(resources, id.withPath(path -> "models/" + path + ".json")));
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                }
            }
            BlockModel raw = parsed.get(id);
            return raw == null ? model : raw;
        };
    }
}

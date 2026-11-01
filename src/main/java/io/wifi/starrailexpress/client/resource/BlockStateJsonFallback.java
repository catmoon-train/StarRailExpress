package io.wifi.starrailexpress.client.resource;

import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 热缓存未覆盖的方块按 ID 去重，并按原版优先级读取其完整 blockstates JSON 资源栈。 */
public final class BlockStateJsonFallback {
    private BlockStateJsonFallback() { }

    public static Set<ResourceLocation> uniqueBlocks(Collection<ModelResourceLocation> missing) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (ModelResourceLocation model : missing) {
            result.add(model.id());
        }
        return result;
    }

    public static List<BlockStateModelLoader.LoadedJson> load(ResourceManager resources, ResourceLocation file) throws IOException {
        List<BlockStateModelLoader.LoadedJson> result = new ArrayList<>();
        for (var resource : resources.getResourceStack(file)) {
            try (var reader = resource.openAsReader()) {
                result.add(new BlockStateModelLoader.LoadedJson(resource.sourcePackId(), GsonHelper.parse(reader)));
            }
        }
        return List.copyOf(result);
    }
}

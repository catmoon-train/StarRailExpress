package io.wifi.starrailexpress.client.resource;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** 锦致装饰的模型每轮正常烘焙，不参与 DashLoader 模型缓存。 */
public final class DashModelCacheExclusions {
    private DashModelCacheExclusions() { }

    public static boolean excluded(ResourceLocation id) {
        return "supplementaries".equals(id.getNamespace());
    }

    /** 保存时使用独立快照，不修改本轮正在使用的烘焙模型表。 */
    public static <T> Map<ModelResourceLocation, T> filtered(Map<ModelResourceLocation, T> models) {
        Map<ModelResourceLocation, T> result = new HashMap<>(models);
        result.keySet().removeIf(id -> excluded(id.id()));
        return result;
    }

    /** 旧缓存中的排除项也不能注入；其方块状态交回现有 JSON 回退。 */
    public static <S> void restoreFallback(Map<ModelResourceLocation, ?> models,
                                         Map<S, ModelResourceLocation> missing,
                                         Map<S, ModelResourceLocation> excludedStates) {
        models.keySet().removeIf(id -> excluded(id.id()));
        missing.putAll(excludedStates);
    }
}

package io.wifi.starrailexpress.mixin.compat.dashloader;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.resource.DashLoaderCompat;
import io.wifi.starrailexpress.client.resource.ModelJsonFallback;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** DashLoader 省略 JSON 扫描后，仍为不支持序列化的自定义模型及其 parent 提供按需回退。 */
@Mixin(ModelBakery.class)
public abstract class DashModelFallbackMixin {
    @Unique
    private final Map<ResourceLocation, BlockModel> sre$fallbackModels = new HashMap<>();

    @WrapOperation(method = "loadBlockModel", at = @At(value = "INVOKE", target =
            "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 1))
    private Object sre$loadUncachedModel(Map<ResourceLocation, BlockModel> models, Object key,
                                        Operation<Object> original) throws IOException {
        Object existing = original.call(models, key);
        if (existing != null || DashLoaderCompat.fallbackResources() == null) {
            return existing;
        }
        ResourceLocation file = (ResourceLocation) key;
        if (!sre$fallbackModels.containsKey(file)) {
            sre$fallbackModels.put(file, ModelJsonFallback.load(DashLoaderCompat.fallbackResources(), file));
        }
        return sre$fallbackModels.get(file);
    }
}

package io.wifi.starrailexpress.mixin.compat.dashloader;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.resource.DashLoaderCompat;
import io.wifi.starrailexpress.client.resource.ModelJsonFallback;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Moonlight 嵌套模型需要原始 JSON 定义，不能将 DashLoader 已烘焙包装强转为 BlockModel。 */
@Mixin(targets = "net.mehvahdjukaar.moonlight.api.client.model.CustomModelLoader", remap = false)
public interface DashMoonlightNestedModelMixin {
    @WrapOperation(method = "parseModel", remap = false, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/resources/model/ModelBaker;getModel(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/resources/model/UnbakedModel;",
            ordinal = 0, remap = true))
    private static UnbakedModel sre$readNestedDefinition(ModelBaker baker, ResourceLocation id,
                                                Operation<UnbakedModel> original) {
        UnbakedModel model = original.call(baker, id);
        return ModelJsonFallback.definition(DashLoaderCompat.fallbackResources(), id, model);
    }
}

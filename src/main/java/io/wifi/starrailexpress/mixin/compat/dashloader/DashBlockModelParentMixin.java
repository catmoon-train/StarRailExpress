package io.wifi.starrailexpress.mixin.compat.dashloader;

import io.wifi.starrailexpress.client.resource.DashLoaderCompat;
import io.wifi.starrailexpress.client.resource.ModelJsonFallback;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Function;

/** 热缓存中的 BakedModel 包装不具备 JSON 继承信息；只对 parent 查找提供原始模型回退。 */
@Mixin(BlockModel.class)
public abstract class DashBlockModelParentMixin {
    @ModifyVariable(method = "resolveParents", at = @At("HEAD"), argsOnly = true)
    private Function<ResourceLocation, UnbakedModel> sre$resolveRawParents(Function<ResourceLocation, UnbakedModel> getter) {
        var resources = DashLoaderCompat.fallbackResources();
        return resources == null ? getter : ModelJsonFallback.parents(resources, getter);
    }
}

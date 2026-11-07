package io.wifi.starrailexpress.mixin.compat.dashloader;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.wifi.starrailexpress.client.resource.DashLoaderCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 锦致装饰不写入或注入预烘焙模型缓存，其他模型缓存仍然启用。 */
@Pseudo
@Mixin(targets = "dev.notalpha.dashloader.client.model.ModelModule", remap = false)
public abstract class DashModelCacheExclusionsMixin {
    @ModifyExpressionValue(method = "save", at = @At(value = "INVOKE",
            target = "Ldev/notalpha/dashloader/api/CachingData;get(Ldev/notalpha/dashloader/api/cache/CacheStatus;)Ljava/lang/Object;"),
            remap = false)
    private Object sre$excludeSavedModels(Object models) {
        return DashLoaderCompat.filterSavedModels(models);
    }

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private void sre$excludeLoadedModels(CallbackInfo ci) {
        DashLoaderCompat.restoreExcludedModelFallbacks();
    }
}

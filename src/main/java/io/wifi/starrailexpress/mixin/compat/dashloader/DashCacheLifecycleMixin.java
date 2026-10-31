package io.wifi.starrailexpress.mixin.compat.dashloader;

import io.wifi.starrailexpress.client.resource.DashCacheFiles;
import io.wifi.starrailexpress.client.resource.DashLoaderCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

/** 部分图集目录不是完整缓存；F3+T 强制刷新时也不能短路模型准备。 */
@Pseudo
@Mixin(targets = "dev.notalpha.dashloader.CacheImpl", remap = false)
public abstract class DashCacheLifecycleMixin {
    @Shadow(remap = false)
    public abstract Path getDir();

    @Inject(method = "exists", at = @At("HEAD"), cancellable = true, remap = false)
    private void sre$completeCacheOnly(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(!DashLoaderCompat.isFreshReload() && DashCacheFiles.complete(getDir()));
    }
}

package io.wifi.starrailexpress.mixin.compat.dashloader;

import com.bawnorton.mixinsquared.TargetHandler;
import io.wifi.starrailexpress.client.resource.DashLoaderCompat;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** 在准备任务启动前装载正确缓存，取代 DashLoader 在 RETURN 才装载的旧钩子。 */
@Mixin(value = ReloadableResourceManager.class, priority = 1500)
public abstract class DashResourceReloadMixin {
    @Shadow @Final private PackType type;

    @Inject(method = "createReload", at = @At("HEAD"))
    private void sre$prepareCacheFirst(Executor prepareExecutor, Executor applyExecutor,
                                      CompletableFuture<Unit> initialStage, List<PackResources> packs,
                                      CallbackInfoReturnable<ReloadInstance> cir) {
        if (type == PackType.CLIENT_RESOURCES) {
            DashLoaderCompat.beginReload(packs, (ReloadableResourceManager) (Object) this);
        }
    }

    @TargetHandler(mixin = "dev.notalpha.dashloader.mixin.main.ReloadableResourceManagerImplMixin",
            name = "reloadDash", prefix = "handler")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
    private void sre$skipLateCacheReset(Executor prepareExecutor, Executor applyExecutor,
                                       CompletableFuture<Unit> initialStage, List<PackResources> packs,
                                       CallbackInfoReturnable<ReloadInstance> originalCi, CallbackInfo ci) {
        // 也不能让 DashLoader 客户端缓存介入数据包重载。
        ci.cancel();
    }
}

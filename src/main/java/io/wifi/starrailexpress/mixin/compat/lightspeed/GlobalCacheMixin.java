package io.wifi.starrailexpress.mixin.compat.lightspeed;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/** 修复集成服收尾后首次返回原版标题页时，Lightspeed 重复向已关闭线程池提交缓存任务。 */
@Pseudo
@Mixin(targets = "com.ccr4ft3r.lightspeed.cache.GlobalCache", remap = false)
public abstract class GlobalCacheMixin {
    @Unique
    private static final AtomicBoolean sre$startupCacheFinishStarted = new AtomicBoolean();

    @Inject(method = "finishStartupCaches()V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sre$finishStartupCachesOnce(CallbackInfo ci) {
        // 客户端标题页与 SERVER_STARTED 共用全局缓存，不能分别执行一次。
        // 在原逻辑提交异步任务前抢占；即使首次任务尚未完成，也不可再次提交或替换其 future。
        if (!sre$startupCacheFinishStarted.compareAndSet(false, true)) {
            ci.cancel();
        }
    }
}

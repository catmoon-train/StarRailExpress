package io.wifi.starrailexpress.mixin.compat.cobblemon;

import io.wifi.starrailexpress.SREConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 拦住关闭前排队或其他刷新器提交的动作，保留 complete() 对 future 的正常收尾。 */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.api.spawning.detail.SingleEntitySpawnAction", remap = false)
public class SingleEntitySpawnActionMixin {
    @Inject(method = "run()Lcom/cobblemon/mod/common/api/spawning/detail/EntitySpawnResult;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void sre$blockDisabledSpawn(CallbackInfoReturnable<Object> cir) {
        if (!SREConfig.instance().pokmen) cir.setReturnValue(null);
    }
}

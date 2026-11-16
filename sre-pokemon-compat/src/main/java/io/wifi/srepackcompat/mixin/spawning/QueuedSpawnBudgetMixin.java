package io.wifi.srepackcompat.mixin.spawning;

import io.wifi.srepackcompat.cobblemon.CobblemonSpawnBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 关闭时拦住已排队/其他刷新器的动作；保留父类 complete() 对 future 的收尾。 */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.api.spawning.detail.SingleEntitySpawnAction", remap = false)
public class QueuedSpawnBudgetMixin {
    @Inject(method = "run()Lcom/cobblemon/mod/common/api/spawning/detail/EntitySpawnResult;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void sre$blockQueuedSpawn(CallbackInfoReturnable<Object> cir) {
        if (!CobblemonSpawnBudget.enabled()) cir.setReturnValue(null);
    }
}

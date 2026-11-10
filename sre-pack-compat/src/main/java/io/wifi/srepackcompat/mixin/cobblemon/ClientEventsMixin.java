package io.wifi.srepackcompat.mixin.cobblemon;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只阻断客户端玩法事件，登录/退出清理、存储同步及服务端事件不丢弃。 */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.api.reactive.SimpleObservable", remap = false)
public class ClientEventsMixin {
    @Inject(method = "emit", at = @At("HEAD"), cancellable = true)
    private void sre$clientEvents(Object[] values, CallbackInfo ci) {
        if (CobblemonPolicy.enabled() || values.length == 0) return;
        for (Object value : values) {
            if (value == null || !CobblemonPolicy.isClientGameplayEvent(value.getClass().getName())) return;
        }
        ci.cancel();
    }
}

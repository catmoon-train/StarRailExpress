package io.wifi.srepokemoncompat.mixin.journeymap;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 保留任务队列，重新授权后原 MappingMonitorTask 会按当前世界自动恢复。 */
@Pseudo
@Mixin(targets = "journeymap.client.event.handlers.StateTickHandler", remap = false)
public class StateTickMixin {
    @Inject(method = "onClientTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void sre$requireServerPermission(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) ci.cancel();
    }
}

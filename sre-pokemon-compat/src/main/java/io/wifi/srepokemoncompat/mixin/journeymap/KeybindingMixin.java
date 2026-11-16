package io.wifi.srepokemoncompat.mixin.journeymap;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 同时隔离世界按键与 JourneyMap 自己的 GUI 原始按键处理。 */
@Pseudo
@Mixin(targets = "journeymap.client.event.handlers.KeybindingHandler", remap = false)
public class KeybindingMixin {
    @Inject(method = "consume", at = @At("HEAD"), cancellable = true, require = 3)
    private void sre$requireServerPermission(CallbackInfoReturnable<Boolean> cir) {
        if (!CobblemonPolicy.enabled()) cir.setReturnValue(false);
    }
}

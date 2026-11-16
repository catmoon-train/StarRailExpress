package io.wifi.srepokemoncompat.mixin.journeymap;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "journeymap.client.event.handlers.HudOverlayHandler", remap = false)
public class HudOverlayMixin {
    // preOverlay 会 push 矩阵；关闭时必须与 postOverlay 一起跳过。
    @Inject(method = "preOverlay", at = @At("HEAD"), cancellable = true, require = 1)
    private void sre$skipPotionShift(CallbackInfoReturnable<Boolean> cir) {
        if (!CobblemonPolicy.enabled()) cir.setReturnValue(false);
    }

    @Inject(method = {"postOverlay", "onRenderOverlay", "renderWaypointDecos", "onRenderOverlayDebug"},
            at = @At("HEAD"), cancellable = true, require = 4)
    private void sre$requireServerPermission(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) ci.cancel();
    }
}

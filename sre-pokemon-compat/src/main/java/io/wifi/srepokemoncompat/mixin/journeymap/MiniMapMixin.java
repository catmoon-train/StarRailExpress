package io.wifi.srepokemoncompat.mixin.journeymap;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 同时覆盖 HUD、其他界面后方和预览路径的直接绘制。 */
@Pseudo
@Mixin(targets = "journeymap.client.ui.minimap.MiniMap", remap = false)
public class MiniMapMixin {
    @Inject(method = "drawMap", at = @At("HEAD"), cancellable = true, require = 2)
    private void sre$requireServerPermission(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) ci.cancel();
    }
}

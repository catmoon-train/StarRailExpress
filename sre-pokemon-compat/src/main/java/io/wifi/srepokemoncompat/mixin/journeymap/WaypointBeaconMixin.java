package io.wifi.srepokemoncompat.mixin.journeymap;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "journeymap.client.event.handlers.WaypointBeaconHandler", remap = false)
public class WaypointBeaconMixin {
    @Inject(method = "onRenderWaypoints", at = @At("HEAD"), cancellable = true, require = 2)
    private void sre$requireServerPermission(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) ci.cancel();
    }
}

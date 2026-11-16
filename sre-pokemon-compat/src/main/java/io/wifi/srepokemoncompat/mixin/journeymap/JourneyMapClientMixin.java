package io.wifi.srepokemoncompat.mixin.journeymap;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import io.wifi.srepokemoncompat.journeymap.JourneyMapControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 阻止后台测绘及 Web 地图；不伪造 isMapping，否则原停止流程无法保存和清理。 */
@Pseudo
@Mixin(targets = "journeymap.client.JourneymapClient", remap = false)
public abstract class JourneyMapClientMixin implements JourneyMapControl {
    @Shadow public abstract Boolean isMapping();
    @Shadow public abstract Boolean started();
    @Shadow public abstract void stopMapping();
    @Shadow public abstract void stopWebMap();

    @Override public void sre$stopMapping() {
        if (Boolean.TRUE.equals(isMapping()) || Boolean.TRUE.equals(started())) stopMapping();
        stopWebMap();
    }

    @Inject(method = {"startMapping", "startWebMap", "performMultithreadTasks", "queueOneOff"},
            at = @At("HEAD"), cancellable = true, require = 4)
    private void sre$requireServerPermission(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) ci.cancel();
    }
}

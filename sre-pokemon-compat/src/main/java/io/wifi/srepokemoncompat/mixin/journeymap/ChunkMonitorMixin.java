package io.wifi.srepokemoncompat.mixin.journeymap;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 不收集关闭期间的区块；卸载、丢弃区块和 reset 清理始终保留。 */
@Pseudo
@Mixin(targets = "journeymap.client.event.handlers.ChunkMonitorHandler", remap = false)
public class ChunkMonitorMixin {
    @Inject(method = {"onChunkLoad", "onBlockUpdate", "onChunkUpdate", "scheduleResetRenderTimes", "scheduleNeighborResets"},
            at = @At("HEAD"), cancellable = true, require = 5)
    private void sre$requireServerPermission(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) ci.cancel();
    }
}

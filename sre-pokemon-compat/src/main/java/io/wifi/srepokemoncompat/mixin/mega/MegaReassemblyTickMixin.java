package io.wifi.srepokemoncompat.mixin.mega;

import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 暂停重组进度，不重置方块实体状态，不影响读写存档与同步。 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.block.block_entity.ReassemblyUnitBlockEntity", remap = false)
public abstract class MegaReassemblyTickMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private static void sre$pauseReassembly(CallbackInfo ci) {
        if (!CobblemonMechanics.enabled()) ci.cancel();
    }
}

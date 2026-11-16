package io.wifi.srepokemoncompat.mixin.mega;

import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只暂停极巨菇生长，保留方块注册、碰撞、掉落和普通拆除。 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.block.custom.MaxMushroomBlock", remap = false)
public abstract class MegaMushroomGrowthMixin {
    @Inject(method = {"randomTick", "method_9514", "performBonemeal", "method_9652"},
            at = @At("HEAD"), cancellable = true, require = 2)
    private void sre$pauseGrowth(CallbackInfo ci) {
        if (!CobblemonMechanics.enabled()) ci.cancel();
    }
}

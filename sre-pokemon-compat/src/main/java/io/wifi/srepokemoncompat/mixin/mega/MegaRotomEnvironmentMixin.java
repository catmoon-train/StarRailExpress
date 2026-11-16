package io.wifi.srepokemoncompat.mixin.mega;

import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 关闭碰撞触发的洛托姆变形与家电方块销毁，不改变碰撞形状。 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.block.custom.RotomUnitBlock", remap = false)
public abstract class MegaRotomEnvironmentMixin {
    @Inject(method = {"entityInside", "method_9548"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void sre$pauseRotom(CallbackInfo ci) {
        if (!CobblemonMechanics.enabled()) ci.cancel();
    }
}

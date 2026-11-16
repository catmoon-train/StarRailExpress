package io.wifi.srepokemoncompat.mixin.mega.client;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只暂停 Mega 环境装饰粒子，不影响普通方块动画。 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.block.custom.ParticleBlock", remap = false)
public abstract class MegaParticleBlockMixin {
    @Inject(method = {"animateTick", "method_9496"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void sre$noMegaParticles(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) ci.cancel();
    }
}

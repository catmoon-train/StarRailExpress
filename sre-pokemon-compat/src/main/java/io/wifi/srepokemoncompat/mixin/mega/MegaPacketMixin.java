package io.wifi.srepokemoncompat.mixin.mega;

import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 五个 C2S 接收器全部是玩法请求；保留协议注册、解码与所有 S2C 同步。 */
@Pseudo
@Mixin(targets = {
        "com.github.yajatkaul.mega_showdown.networking.server.handler.CrownHandler",
        "com.github.yajatkaul.mega_showdown.networking.server.handler.MegaEvoHandler",
        "com.github.yajatkaul.mega_showdown.networking.server.handler.PrimalEvoHandler",
        "com.github.yajatkaul.mega_showdown.networking.server.handler.SecretSwordMoveSwapHandler",
        "com.github.yajatkaul.mega_showdown.networking.server.handler.UltraBurstHandler"
}, remap = false)
public abstract class MegaPacketMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 1)
    private static void sre$gameplayRequest(CallbackInfo ci) {
        if (!CobblemonMechanics.enabled()) ci.cancel();
    }
}

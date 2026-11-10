package io.wifi.srepackcompat.mixin.cobblemon;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.client.sound.BattleMusicController", remap = false)
public class BattleMusicMixin {
    @Inject(method = {"initializeMusic", "switchMusic"}, at = @At("HEAD"), cancellable = true)
    private void sre$music(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) ci.cancel();
    }
}

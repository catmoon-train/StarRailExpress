package io.wifi.srepackcompat.mixin.cobblemon;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.pokedex.scanner.PokedexUsageContext", remap = false)
public class PokedexMixin {
    @Inject(method = {"renderUpdate", "useTick", "tryOpenScanGui", "openPokedexGUI", "attackKeyHeld", "tryScanPokemon", "playSound"},
            at = @At("HEAD"), cancellable = true)
    private void sre$stopScanner(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) ci.cancel();
    }

    @Inject(method = {"getScanningGuiOpen", "getInfoGuiOpen"}, at = @At("HEAD"), cancellable = true)
    private void sre$noInputCapture(CallbackInfoReturnable<Boolean> cir) {
        if (!CobblemonPolicy.enabled()) cir.setReturnValue(false);
    }
}

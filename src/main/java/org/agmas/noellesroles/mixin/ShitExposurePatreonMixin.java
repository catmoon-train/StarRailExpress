// Patreon
package org.agmas.noellesroles.mixin;

import io.github.mortuusars.exposure.util.supporter.Gilded;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;

@Mixin(Gilded.class)
public abstract class ShitExposurePatreonMixin {
    @Inject(method = "getUuidsUri", at = @At("TAIL"), order = 10000, cancellable = true)
    private void shitExposure(CallbackInfoReturnable<URI> cir){
        cir.setReturnValue(null);
        cir.cancel();
    }
}
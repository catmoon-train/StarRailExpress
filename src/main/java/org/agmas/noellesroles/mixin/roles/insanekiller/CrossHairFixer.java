package org.agmas.noellesroles.mixin.roles.insanekiller;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import walksy.crosshairaddons.CrosshairAddons;
import walksy.crosshairaddons.manager.AddonStateManager;
import walksy.crosshairaddons.manager.State;

//防止躺下的亡语被准心看到
@Mixin(AddonStateManager.class)
public class CrossHairFixer
{
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    public void get(State state, CallbackInfoReturnable<Boolean> cir)
    {
        if (state == State.INDICATOR){
            cir.setReturnValue(false);
            cir.cancel();
        }

    }

}

package net.exmo.mixin.client;

import net.exmo.sre.loading.StarRailExpressTitleScreen;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public class VolumeGetMixin {
   @Inject(method = "getSoundSourceVolume", at = @At("RETURN"), cancellable = true)
    public void getSoundSourceVolume(SoundSource soundSource, CallbackInfoReturnable<Float> cir) {
        if (StarRailExpressTitleScreen.voiceFadeInDuration>0){
            cir.setReturnValue(cir.getReturnValueF()*StarRailExpressTitleScreen.voiceFadeInDuration/40);
        }
    }

}

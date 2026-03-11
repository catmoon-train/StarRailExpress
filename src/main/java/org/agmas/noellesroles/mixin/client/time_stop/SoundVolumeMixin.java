package org.agmas.noellesroles.mixin.client.time_stop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public class SoundVolumeMixin {
    @Inject(method = "getSoundSourceVolume", at = @At("HEAD"), cancellable = true)
    public void getSoundSourceVolume(SoundSource soundSource, CallbackInfoReturnable<Float> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player !=null){
            if (player.hasEffect(ModEffects.TIME_STOP)){
                if (!TimeStopEffect.canMovePlayers.contains(player.getUUID())) {
                    cir.setReturnValue(0.0f);
                }
            }
        }
    }
}

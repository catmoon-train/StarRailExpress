package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class PlayerIsolationSoundMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void blockPlayerSound(SoundInstance sound, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.hasEffect(ModEffects.PLAYER_ISOLATION)) return;
        if (sound.getSource() == SoundSource.PLAYERS) {
            ci.cancel();
        }
    }
}

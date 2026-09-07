package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.agmas.noellesroles.client.BlindnessVisionClientHandle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * SoundManager listeners receive new sounds, but vanilla does not re-emit
 * looping sounds and jukebox records every tick. The original Forge mod added
 * exactly this supplementary sampling in SoundEngine#tickNonPaused.
 */
@Mixin(SoundEngine.class)
public abstract class BlindnessSoundMixin {
    @Shadow @Final private List<TickableSoundInstance> tickingSounds;

    @Inject(method = "play", at = @At("TAIL"))
    private void starrailexpress$recordPlayedSound(SoundInstance sound, CallbackInfo ci) {
        // SoundManager listeners are not guaranteed to receive every sound when
        // another sound mixin/resource reload is active. The Forge mod captured
        // the same play event directly from SoundEngine, so keep this direct hook
        // as the authoritative fallback.
        BlindnessVisionClientHandle.recordPlayedSound(sound);
    }

    @Inject(method = "tickNonPaused", at = @At("HEAD"))
    private void starrailexpress$samplePersistentSounds(CallbackInfo ci) {
        for (TickableSoundInstance sound : tickingSounds) {
            if (!sound.isStopped() && sound.canPlaySound()) {
                BlindnessVisionClientHandle.recordLoopingSound(sound);
            }
        }

        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer instanceof BlindnessLevelRendererAccessor accessor) {
            for (SoundInstance sound : accessor.starrailexpress$getPlayingJukeboxSongs().values()) {
                BlindnessVisionClientHandle.recordLoopingSound(sound);
            }
        }
    }
}

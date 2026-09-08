package org.agmas.noellesroles.mixin.client.general;

import com.mojang.blaze3d.audio.Channel;
import org.agmas.noellesroles.client.MuffledHearingClientHandle;
import org.agmas.noellesroles.client.audio.MuffledHearingChannelAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 给所有 Minecraft 原生音频通道挂上听觉模糊的低通滤波器。 */
@Mixin(value = Channel.class, remap = false)
public abstract class MuffledHearingChannelMixin implements MuffledHearingChannelAccess {
    @Shadow @Final private int source;

    @Inject(method = "setVolume", at = @At("TAIL"), remap = false)
    private void noellesroles$refreshFilter(float volume, CallbackInfo ci) {
        MuffledHearingClientHandle.applyToSource(source);
    }

    @Inject(method = "play", at = @At("HEAD"), remap = false)
    private void noellesroles$applyFilterBeforePlay(CallbackInfo ci) {
        // setVolume can run before the source is fully prepared. Apply it
        // once more immediately before OpenAL starts the source.
        noellesroles$applyMuffledHearing();
    }

    @Inject(method = "attachStaticBuffer", at = @At("TAIL"), remap = false)
    private void noellesroles$applyFilterToStaticBuffer(com.mojang.blaze3d.audio.SoundBuffer buffer,
            CallbackInfo ci) {
        noellesroles$applyMuffledHearing();
    }

    @Inject(method = "attachBufferStream", at = @At("TAIL"), remap = false)
    private void noellesroles$applyFilterToStream(net.minecraft.client.sounds.AudioStream stream,
            CallbackInfo ci) {
        noellesroles$applyMuffledHearing();
    }

    @Override
    @Unique
    public void noellesroles$applyMuffledHearing() {
        MuffledHearingClientHandle.applyToSource(source);
    }
}

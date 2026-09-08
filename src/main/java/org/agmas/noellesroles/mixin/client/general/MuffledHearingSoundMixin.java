package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.agmas.noellesroles.client.MuffledHearingClientHandle;
import org.agmas.noellesroles.client.audio.MuffledHearingChannelAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 每 tick 刷新已在播放的音效，保证效果中途获得/结束时也能正确切换。 */
@Mixin(SoundEngine.class)
public abstract class MuffledHearingSoundMixin {
    @Shadow @Final private ChannelAccess channelAccess;

    @Inject(method = "destroy", at = @At("HEAD"))
    private void noellesroles$resetOpenALFilter(CallbackInfo ci) {
        MuffledHearingClientHandle.resetOpenALState();
    }

    @Inject(method = "tickNonPaused", at = @At("HEAD"))
    private void noellesroles$refreshMuffledHearing(CallbackInfo ci) {
        if (!MuffledHearingClientHandle.active && !MuffledHearingClientHandle.hasFilter()) {
            return;
        }
        channelAccess.executeOnChannels(channels -> channels.forEach(channel -> {
            if (channel instanceof MuffledHearingChannelAccess access) {
                access.noellesroles$applyMuffledHearing();
            }
        }));
    }
}

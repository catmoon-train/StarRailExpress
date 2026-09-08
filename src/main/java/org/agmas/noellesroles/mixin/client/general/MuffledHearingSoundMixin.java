/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
        // Also run while the effect is inactive. This immediately removes a
        // filter left on a source when the potion expires or is cleared.
        channelAccess.executeOnChannels(channels -> channels.forEach(channel -> {
            if (channel instanceof MuffledHearingChannelAccess access) {
                access.noellesroles$applyMuffledHearing();
            }
        }));
    }
}

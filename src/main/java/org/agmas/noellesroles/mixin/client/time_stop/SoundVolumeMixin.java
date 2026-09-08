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

package org.agmas.noellesroles.mixin.client.time_stop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
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
        if (player != null) {
            if (player.hasEffect(ModEffects.DEAFNESS)) {
                cir.setReturnValue(0.0f);
                return;
            }
            if (player.hasEffect(ModEffects.TIME_STOP)) {
                if (!TimeStopEffect.clientCanMovePlayers.contains(player.getUUID())) {
                    cir.setReturnValue(0.0f);
                }
            }
        }
    }

    @Inject(method = "getSoundSourceVolume", at = @At("RETURN"), cancellable = true)
    private void noellesroles$reduceMuffledHearingVolume(SoundSource soundSource,
            CallbackInfoReturnable<Float> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(ModEffects.MUFFLED_HEARING)) {
            int level = ModEffects.getMuffledHearingLevel(player);
            // The low-pass filter handles the loss of clarity. Keep enough
            // loudness so the effect does not sound like a mute switch.
            // Level I keeps the old level-V loudness; higher levels reduce it
            // further while the filter removes clarity.
            float gain = Math.max(0.45f, 0.72f - Math.max(0, level - 1) * 0.04f);
            cir.setReturnValue(cir.getReturnValue() * gain);
        }
    }
}

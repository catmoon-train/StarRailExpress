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

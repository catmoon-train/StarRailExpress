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

import de.maxhenkel.voicechat.voice.client.SoundManager;
import de.maxhenkel.voicechat.voice.client.speaker.ALSpeakerBase;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.TwoDimensionalCameraClientHandle;
import org.lwjgl.openal.AL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ALSpeakerBase.class, remap = false)
public class VoicechatALSpeakerBaseMixin {
    @Inject(
            method = "setPositionSync",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/openal/AL11;alListener3f(IFFF)V",
                    shift = At.Shift.AFTER),
            remap = false)
    private void noellesroles$usePlayerAsOpenALListener(Vec3 soundPosition, float distance, CallbackInfo ci) {
        Vec3 listener = TwoDimensionalCameraClientHandle.listenerPosition();
        if (listener == null) {
            return;
        }
        AL11.alListener3f(AL11.AL_POSITION, (float) listener.x, (float) listener.y, (float) listener.z);
        SoundManager.checkAlError();
    }
}

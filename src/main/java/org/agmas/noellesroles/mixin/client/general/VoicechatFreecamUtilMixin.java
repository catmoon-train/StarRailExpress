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

import de.maxhenkel.voicechat.integration.freecam.FreecamUtil;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.TwoDimensionalCameraClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FreecamUtil.class, remap = false)
public class VoicechatFreecamUtilMixin {
    @Inject(method = "getReferencePoint", at = @At("HEAD"), cancellable = true, remap = false)
    private static void noellesroles$usePlayerAsVoiceReference(CallbackInfoReturnable<Vec3> cir) {
        Vec3 listener = TwoDimensionalCameraClientHandle.listenerPosition();
        if (listener != null) {
            cir.setReturnValue(listener);
        }
    }
}

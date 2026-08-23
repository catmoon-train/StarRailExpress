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

package io.wifi.starrailexpress.mixin.input;

import io.wifi.starrailexpress.client.SecurityCameraClientState;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {


    //we need here so we can cancel normal press. cant use event
    @Inject(method = "keyPress",
            at = @At(target = "Lcom/mojang/blaze3d/platform/InputConstants;getKey(II)Lcom/mojang/blaze3d/platform/InputConstants$Key;", value = "INVOKE",
                    shift = At.Shift.BEFORE),
            cancellable = true)
    protected void supp$onKeyPressCancellable(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (SecurityCameraClientState.onEarlyKeyPress(key, scanCode, action, modifiers)) {
            ci.cancel();
        }
    }
}

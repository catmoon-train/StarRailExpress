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

package org.agmas.noellesroles.mixin.client.fake_steve;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.agmas.noellesroles.client.FakeSteveClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies server-owned movement after physical keyboard state has been sampled. */
@Mixin(KeyboardInput.class)
public abstract class FakeSteveClientInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void noellesroles$applyFakeSteveInput(boolean sneaking, float sneakSpeed,
            CallbackInfo ci) {
        FakeSteveClient.applyAiInput((Input) (Object) this);
    }
}

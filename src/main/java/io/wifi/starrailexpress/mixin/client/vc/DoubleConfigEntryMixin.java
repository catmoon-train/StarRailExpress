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

package io.wifi.starrailexpress.mixin.client.vc;

import de.maxhenkel.voicechat.configbuilder.entry.DoubleConfigEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DoubleConfigEntry.class)
public abstract class DoubleConfigEntryMixin {

    @Inject(method = "fixValue", at = @At("HEAD"), cancellable = true)
    private void modifyMax(Double value, CallbackInfoReturnable<Double> cir) {
        var entry = (DoubleConfigEntry) (Object) this;
        cir.setReturnValue(Math.max(Math.min(value, (Double) entry.getMax()), (Double) entry.getMin()));
        return;
    }
}
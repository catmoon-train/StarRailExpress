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

import de.maxhenkel.voicechat.configbuilder.entry.AbstractRangedConfigEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractRangedConfigEntry.class)
public abstract class BetterVoiceChatVolumeManageMixin<T> {
    
    @SuppressWarnings("unchecked")
    @Inject(
        method = "getMax",
        at = @At("RETURN"),
        cancellable = true
    )
    private void modifyMax(CallbackInfoReturnable<T> cir) {
        var entry = (AbstractRangedConfigEntry<T>)(Object)this;
        if ("voice_chat_volume".equals(entry.getKey()) && cir.getReturnValue() instanceof Double) {
            cir.setReturnValue((T) Double.valueOf(10.0));
            return;
        }
    }
}
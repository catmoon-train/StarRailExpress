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

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.CommonTickingComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SREGameTimeComponent.class)
public abstract class GameTimeFreezeMixin implements AutoSyncedComponent, CommonTickingComponent {
    @Inject(method = "getTime", at = @At("HEAD"), cancellable = true)
    public void getTime(CallbackInfoReturnable<Integer> cir) {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().player != null)
            if (Minecraft.getInstance().player.hasEffect(ModEffects.TIME_STOP)) {
                cir.setReturnValue(TimeStopEffect.freezeStatedTime);
                cir.cancel();
            }
    }
}

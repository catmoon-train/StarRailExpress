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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityUseCallbackMixin {
    @Inject(method = "getUseItemRemainingTicks", at = @At("HEAD"), cancellable = true)
    public void getUseItemRemainingTicks(CallbackInfoReturnable<Integer> cir) {
        if (!(((LivingEntity) (Object) this) instanceof Player))
            return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player!=null){
            if (player.hasEffect(ModEffects.TIME_STOP)){
                if (!TimeStopEffect.canMovePlayers.contains(player.getUUID())){
                    cir.setReturnValue(0);
                }
            }
        }
    }
}

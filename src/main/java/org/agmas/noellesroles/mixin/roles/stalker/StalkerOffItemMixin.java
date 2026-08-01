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

package org.agmas.noellesroles.mixin.roles.stalker;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class StalkerOffItemMixin {

    @Inject(method = "getMainArm", at = @At("RETURN"), cancellable = true)
    private void mainHand(CallbackInfoReturnable<HumanoidArm> cir) {
        final Player player = (Player) (Object) this;
        if (player != null) {
            if (player.getMainHandItem().getItem() == ModItems.STALKER_KNIFE_OFFHAND) {
                if (cir.getReturnValue() == HumanoidArm.RIGHT) {
                    cir.setReturnValue(HumanoidArm.LEFT);
                } else {
                    cir.setReturnValue(HumanoidArm.RIGHT);
                }
            }
        }
    }

}

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

package org.agmas.noellesroles.mixin.time_stop;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.DIORoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "isSwimming",at = @At("HEAD"), cancellable = true)
    public void isSwim(CallbackInfoReturnable<Boolean> cir){
        Player player = (Player) (Object)this;
        var repairComponent = ModComponents.REPAIR_ROLES.get(player);
        if (repairComponent.downed || repairComponent.carriedBy != null || repairComponent.trialStand.present()) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }
        if (SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.DIO)){
            if (RoleData.getOptional(DIORoleData.class, player).map(d -> d.isFeeding).orElse(false)){
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

}

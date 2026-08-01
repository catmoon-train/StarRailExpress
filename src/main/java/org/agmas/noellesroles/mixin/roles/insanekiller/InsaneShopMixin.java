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

package org.agmas.noellesroles.mixin.roles.insanekiller;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShopEntry.class)
public abstract class InsaneShopMixin {

    @Inject(method = "canBuy", at = @At("HEAD"), cancellable = true)
    private void cantBuy(@NotNull Player player, CallbackInfoReturnable<Boolean> cir) {
        if (SREGameWorldComponent.KEY.get(player.level()).isRole(player,
                ModRoles.INSANE_KILLER)) {
            var ikpc = InsaneKillerPlayerComponent.KEY.get(player);
            if(ikpc.inNearDeath()){
                cir.setReturnValue(false);
                return;
            }
        }
    }
}

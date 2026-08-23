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

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.WraithAssassinRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class WraithVisibilityMixin {
    @Inject(method = "isInvisibleTo", at = @At("RETURN"), cancellable = true)
    private void noellesroles$wraithVisibility(Player viewer, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player target) || !target.hasEffect(ModEffects.WRAITH_DIMENSION)) {
            return;
        }
        if (target.hasEffect(ModEffects.WRAITH_MANIFEST)) {
            cir.setReturnValue(false);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || viewer != mc.player || SREClient.gameComponent == null) {
            return;
        }
        if (!SREClient.gameComponent.isRole(target, ModRoles.WRAITH_ASSASSIN)) {
            return;
        }
        cir.setReturnValue(!WraithAssassinRoleData.canPerceiveWraith(viewer));
    }
}

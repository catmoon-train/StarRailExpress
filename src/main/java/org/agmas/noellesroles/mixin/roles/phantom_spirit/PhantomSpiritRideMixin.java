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

package org.agmas.noellesroles.mixin.roles.phantom_spirit;

import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role_data.neutral.PhantomSpiritRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 幻灵附身期间禁止主动下骑，避免按潜行从宿主头上掉下来。
 */
@Mixin(Player.class)
public abstract class PhantomSpiritRideMixin {

    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void noellesroles$keepPhantomSpiritRiding(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (PhantomSpiritRoleData.shouldStayRiding(self)) {
            cir.setReturnValue(false);
        }
    }
}

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

package org.agmas.noellesroles.mixin.roles.chino;

import org.agmas.noellesroles.role.anime.chino.ChinoHeadRideManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 载具自己的客户端收不到原版乘客列表包，且乘客端的 tracker 插值会与 {@code positionRider} 打架，
 * 所以由载具每 tick 主动把头顶的兔兔摆回挂点（服务端与客户端都执行）。
 */
@Mixin(Player.class)
public abstract class ChinoHeadRidePositionMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void noellesroles$positionRabbitOnHead(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!ChinoHeadRideManager.isCarrying(self)) {
            return;
        }
        Entity rider = self.getFirstPassenger();
        if (rider != null && ChinoHeadRideManager.isCarriedBy(rider, self)) {
            self.positionRider(rider);
        }
    }
}

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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 把被咖啡师抱着的兔兔挂到头顶，而不是埋进身体碰撞箱里；并放行玩家乘客。
 */
@Mixin(Entity.class)
public abstract class ChinoHeadRideAttachMixin {

    @Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
    private void noellesroles$putRabbitOnChinoHead(Entity passenger, EntityDimensions dimensions, float scale,
            CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Player vehicle && ChinoHeadRideManager.isCarriedBy(passenger, self)) {
            cir.setReturnValue(new Vec3(0.0, ChinoHeadRideManager.headPassengerAttachmentY(vehicle, passenger), 0.0));
        }
    }

    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void noellesroles$allowRabbitPassenger(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Player && ChinoHeadRideManager.isCarriedBy(passenger, self)) {
            cir.setReturnValue(self.getPassengers().isEmpty() || self.hasPassenger(passenger));
        }
    }
}

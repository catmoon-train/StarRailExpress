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

package pro.fazeclan.river.stupid_express.mixin.modifier.twin_children;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.fazeclan.river.stupid_express.modifier.twin_children.TwinChildrenHandler;

/**
 * Put the upper twin on the visual head instead of the default sitting point,
 * and allow the paired player to ride.
 */
@Mixin(Entity.class)
public abstract class TwinChildrenAttachMixin {

    @Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
    private void stupidExpress$putTwinOnHead(Entity passenger, EntityDimensions dimensions, float scale,
            CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Player vehicle && passenger instanceof Player rider
                && TwinChildrenHandler.hasHalfScale(vehicle)
                && TwinChildrenHandler.hasHalfScale(rider)) {
            double attachY = TwinChildrenHandler.headPassengerAttachmentY(
                    vehicle.getScale(), rider.getVehicleAttachmentPoint(vehicle).y);
            cir.setReturnValue(new Vec3(0.0, attachY, 0.0));
        }
    }

    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void stupidExpress$allowTwinPassenger(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Player vehicle && passenger instanceof Player rider
                && TwinChildrenHandler.hasHalfScale(vehicle)
                && TwinChildrenHandler.hasHalfScale(rider)) {
            cir.setReturnValue(self.getPassengers().isEmpty() || self.hasPassenger(passenger));
        }
    }
}

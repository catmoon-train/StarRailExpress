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

package org.agmas.noellesroles.mixin.client.roles.chino;

import org.agmas.noellesroles.role.anime.chino.ChinoHeadRideManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 被抱着的兔兔位置由载具的 {@code positionRider} 决定，
 * tracker 的插值会把它从头顶拽走，故骑乘期间跳过插值。
 */
@Mixin(LivingEntity.class)
public abstract class ChinoHeadRideLerpMixin {

    @Inject(method = "lerpTo", at = @At("HEAD"), cancellable = true)
    private void noellesroles$skipCarriedRabbitLerp(double x, double y, double z, float yRot, float xRot, int steps,
            CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player && ChinoHeadRideManager.isCarriedRider(player)) {
            ci.cancel();
        }
    }
}

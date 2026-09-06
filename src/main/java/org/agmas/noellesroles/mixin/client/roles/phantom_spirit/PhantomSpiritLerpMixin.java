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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.mixin.client.roles.phantom_spirit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role_data.neutral.PhantomSpiritRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 附身期间忽略旧的插值包，避免别人看见幻灵停在原地。
 */
@Mixin(LivingEntity.class)
public abstract class PhantomSpiritLerpMixin {

    @Inject(method = "lerpTo", at = @At("HEAD"), cancellable = true)
    private void noellesroles$skipPossessLerp(double x, double y, double z, float yRot, float xRot, int steps,
            CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player && player.getVehicle() instanceof Player
                && PhantomSpiritRoleData.isPossessingSpirit(player)) {
            ci.cancel();
        }
    }
}

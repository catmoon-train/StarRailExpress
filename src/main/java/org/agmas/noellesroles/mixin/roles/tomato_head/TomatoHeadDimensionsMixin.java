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

package org.agmas.noellesroles.mixin.roles.tomato_head;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role_data.innocence.TomatoHeadRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class TomatoHeadDimensionsMixin {

    @ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
    private EntityDimensions noellesroles$tomatoHitbox(EntityDimensions dimensions, Pose pose) {
        Player self = (Player) (Object) this;
        if (!TomatoHeadRoleData.isTomatoForm(self)) {
            return dimensions;
        }
        return EntityDimensions.scalable(TomatoHeadRoleData.TOMATO_WIDTH, TomatoHeadRoleData.TOMATO_HEIGHT)
                .withEyeHeight(TomatoHeadRoleData.TOMATO_EYE_HEIGHT);
    }
}

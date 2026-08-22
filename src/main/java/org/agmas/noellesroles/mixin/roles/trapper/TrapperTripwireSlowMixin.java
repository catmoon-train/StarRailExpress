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

package org.agmas.noellesroles.mixin.roles.trapper;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 绊线 -90% 移速。本游戏覆盖了 {@link Player#getSpeed()}，原版缓慢按每级 -20%
 * 计算，缓慢 VI 会变成移速 0；因此绊线改用独立效果再乘 0.1。
 */
@Mixin(value = Player.class, priority = 1200)
public class TrapperTripwireSlowMixin {

    @ModifyReturnValue(method = "getSpeed", at = @At("RETURN"))
    private float trapper$tripwireSlow(float original) {
        Player player = (Player) (Object) this;
        if (player.hasEffect(ModEffects.TRIPWIRE_SLOW)) {
            return original * 0.1f;
        }
        return original;
    }
}

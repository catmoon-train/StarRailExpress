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

package org.agmas.noellesroles.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.effects.LimpEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 腿瘸时弱侧步伐减速并微微侧倾，形成一瘸一拐。
 * 原版 travel 没有对应事件，只能改移动输入。
 */
@Mixin(LivingEntity.class)
public class LimpTravelMixin {

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3 noellesroles$limpTravel(Vec3 input) {
        LivingEntity self = (LivingEntity) (Object) this;
        int amplifier = LimpEffect.amplifierOf(self);
        if (amplifier < 0 || !self.onGround() || self.isSpectator() || self.isSwimming()
                || self.isFallFlying() || self.getVehicle() != null
                || (self instanceof Player player && player.getAbilities().flying)) {
            return input;
        }
        if (input.horizontalDistanceSqr() < 1.0E-8D) {
            return input;
        }
        if (!LimpEffect.isWeakStep(self, amplifier)) {
            return input;
        }
        float scale = LimpEffect.weakStepScale(amplifier);
        float sway = LimpEffect.sideSway(amplifier);
        return new Vec3(input.x * scale + sway, input.y, input.z * scale);
    }
}

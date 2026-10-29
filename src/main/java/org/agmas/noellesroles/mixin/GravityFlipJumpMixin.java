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
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.effects.GravityFlipEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 重力颠倒时把跳跃方向反过来：从天花板往下蹬，而不是往上跳。
 */
@Mixin(LivingEntity.class)
public abstract class GravityFlipJumpMixin {

    @Shadow
    protected abstract float getJumpPower();

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void noellesroles$jumpAwayFromCeiling(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!GravityFlipEffect.isFlipped(self)) {
            return;
        }
        Vec3 velocity = self.getDeltaMovement();
        self.setDeltaMovement(velocity.x, -getJumpPower(), velocity.z);
        self.setJumping(true);
        ci.cancel();
    }
}

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

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.effects.GravityFlipEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 重力颠倒的物理层
 *
 * <p>{@code Entity#getGravity()} 是 {@code final}，不能被覆写，只能在返回值处取反；
 * {@code Entity#applyGravity()} 拿到负值后会向 +Y 加速，于是玩家向天空「坠落」。
 */
@Mixin(Entity.class)
public abstract class GravityFlipPhysicsMixin {

    /** ① 重力取反：核心。 */
    @Inject(method = "getGravity", at = @At("RETURN"), cancellable = true)
    private void noellesroles$reverseGravity(CallbackInfoReturnable<Double> cir) {
        if (GravityFlipEffect.isFlipped((Entity) (Object) this)) {
            cir.setReturnValue(-cir.getReturnValueD());
        }
    }

    /**
     * ② 落地判定改造：原版只有「向下移动撞到东西」才算落地，
     * 重力颠倒后要改成「向上移动撞到东西（即撞到天花板）」才算落地，
     * 否则玩家会一直贴着天花板且无法跳跃、没有地面摩擦。
     */
    @Inject(method = "move", at = @At("TAIL"))
    private void noellesroles$ceilingIsGround(MoverType type, Vec3 movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!GravityFlipEffect.isFlipped(self)) {
            return;
        }
        self.setOnGround(self.verticalCollision && movement.y > 0.0D);
        // 向上「坠落」不应累计摔落距离
        self.fallDistance = 0.0F;
    }
}

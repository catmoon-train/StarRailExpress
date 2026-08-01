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

package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.client.PointerClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 移动方向被锁到二维镜头朝向后（见 {@link PointerMovementYawMixin}），原版「前进」已不再是 W ——
 * A/S/D 同样是屏幕上的正常移动方向。但原版疾跑只认 {@code forwardImpulse}：
 * 起跑要求它 ≥ 0.8，持续疾跑要求 {@code hasForwardImpulse()}，于是只有按 W 才能疾跑。
 * 这里在锁定期间把两处判定都改成取「前后 / 左右」两轴的较大值。
 */
@Mixin(LocalPlayer.class)
public abstract class PointerSprintDirectionMixin {
    @Unique
    private static final float NOE$SPRINT_START_IMPULSE = 0.8F;
    /** 与 {@code Input.hasForwardImpulse()} 的判定阈值保持一致。 */
    @Unique
    private static final float NOE$MOVE_EPSILON = 1.0E-5F;

    @Shadow
    public Input input;

    @Inject(method = "hasEnoughImpulseToStartSprinting", at = @At("HEAD"), cancellable = true)
    private void noe$sprintStartFromAnyDirection(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (!PointerClientHandle.isMovementYawLocked(self)) {
            return;
        }
        float impulse = noe$maxMoveImpulse();
        cir.setReturnValue(self.isUnderWater()
                ? impulse > NOE$MOVE_EPSILON
                : impulse >= NOE$SPRINT_START_IMPULSE);
    }

    /** aiStep 中唯一一处 {@code hasForwardImpulse()}：疾跑的持续条件（松开 W 即停）。 */
    @Redirect(
            method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;hasForwardImpulse()Z"))
    private boolean noe$sprintKeepFromAnyDirection(Input instance) {
        if (PointerClientHandle.isMovementYawLocked((LocalPlayer) (Object) this)) {
            return noe$maxMoveImpulse() > NOE$MOVE_EPSILON;
        }
        return instance.hasForwardImpulse();
    }

    @Unique
    private float noe$maxMoveImpulse() {
        return Math.max(Math.abs(this.input.forwardImpulse), Math.abs(this.input.leftImpulse));
    }
}

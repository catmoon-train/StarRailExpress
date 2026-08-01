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

import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.client.PointerClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 疾跑起跳的水平冲量同样按 yaw 施加，是 {@link PointerMovementYawMixin} 之外唯一漏掉的移动来源：
 * 不改的话，指针 + 二维视角下疾跑跳会朝指针方向窜出去，而不是屏幕上方。
 */
@Mixin(LivingEntity.class)
public class PointerSprintJumpYawMixin {
    @Redirect(
            method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float noe$pointerSprintJumpYaw(LivingEntity self) {
        if (PointerClientHandle.isMovementYawLocked(self)) {
            return PointerClientHandle.lockedMovementYaw();
        }
        return self.getYRot();
    }
}

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

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.PointerClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 指针效果 + 二维视角下，玩家的移动方向不再跟随（被指针强行掰动的）视角，
 * 而是固定按二维相机的水平朝向：W 走屏幕上方，A/D 走屏幕左右，且方向吸附到 45° 的八个方向。
 * <p>
 * {@code moveRelative} 是原版把 WASD 输入旋转到世界空间的唯一入口（{@code getInputVector} 拿它的
 * 返回值当偏航角），所以只需替换这里的 yaw；瞄准、模型朝向仍由 {@link PointerClientHandle} 决定。
 */
@Mixin(Entity.class)
public class PointerMovementYawMixin {
    @Unique
    private static final double NOE$OCTANT = Math.PI / 4.0D;

    @Redirect(
            method = "moveRelative",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
    private float noe$pointerMovementYaw(Entity self) {
        if (PointerClientHandle.isMovementYawLocked(self)) {
            return PointerClientHandle.lockedMovementYaw();
        }
        return self.getYRot();
    }

    /**
     * 把移动输入吸附到相机空间的八个方向（正上 / 左上 / 正左 / …）。在 {@code getInputVector}
     * 按偏航角旋转之前吸附即可 —— 旋转保角，相对空间的 45° 倍数就是屏幕上的八向。
     * <p>
     * 键盘输入本就落在 45° 上（前后轴与左右轴同幅缩放），此处对手柄 / 控制器模组的模拟摇杆
     * 才有实际作用；长度保持不变，因此不影响移速。
     */
    @ModifyArg(
            method = "moveRelative",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;"),
            index = 0)
    private Vec3 noe$snapToEightDirections(Vec3 relative) {
        if (!PointerClientHandle.isMovementYawLocked((Entity) (Object) this)) {
            return relative;
        }
        double lengthSqr = relative.x * relative.x + relative.z * relative.z;
        if (lengthSqr <= 1.0E-8D) {
            return relative;
        }
        double length = Math.sqrt(lengthSqr);
        double snapped = Math.round(Math.atan2(relative.z, relative.x) / NOE$OCTANT) * NOE$OCTANT;
        return new Vec3(Math.cos(snapped) * length, relative.y, Math.sin(snapped) * length);
    }
}

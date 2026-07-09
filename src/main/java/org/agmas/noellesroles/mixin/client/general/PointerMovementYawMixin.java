package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.client.PointerClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 指针效果 + 二维视角下，玩家的移动方向不再跟随（被指针强行掰动的）视角，
 * 而是固定按二维相机的水平朝向：W 走屏幕上方，A/D 走屏幕左右。
 * <p>
 * {@code moveRelative} 是原版把 WASD 输入旋转到世界空间的唯一入口（{@code getInputVector} 拿它的
 * 返回值当偏航角），所以只需替换这里的 yaw；瞄准、模型朝向仍由 {@link PointerClientHandle} 决定。
 */
@Mixin(Entity.class)
public class PointerMovementYawMixin {
    @Redirect(
            method = "moveRelative",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
    private float noe$pointerMovementYaw(Entity self) {
        if (PointerClientHandle.isMovementYawLocked(self)) {
            return PointerClientHandle.lockedMovementYaw();
        }
        return self.getYRot();
    }
}

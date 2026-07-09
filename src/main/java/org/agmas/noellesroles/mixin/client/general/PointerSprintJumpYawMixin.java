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

package org.agmas.noellesroles.mixin.client.roles.priest;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.PriestHeavenClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 时间加速开始后，把所有人的摄像机从眼睛过渡到正上方并朝下看，直到序列被重置。
 */
@Mixin(value = Camera.class, priority = 2000)
public abstract class PriestHeavenCameraMixin {

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract void setPosition(Vec3 pos);

    @Inject(method = "setup", at = @At("RETURN"))
    private void noellesroles$priestHeavenOverhead(BlockGetter area, Entity focusedEntity, boolean thirdPerson,
            boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (focusedEntity == null) {
            return;
        }
        float blend = PriestHeavenClient.overheadBlend(tickDelta);
        if (blend <= 0.001F) {
            return;
        }
        setPosition(PriestHeavenClient.overheadCameraPos(focusedEntity, tickDelta, blend));
        setRotation(
                PriestHeavenClient.overheadYaw(focusedEntity, tickDelta),
                PriestHeavenClient.overheadPitch(focusedEntity, tickDelta, blend));
    }
}

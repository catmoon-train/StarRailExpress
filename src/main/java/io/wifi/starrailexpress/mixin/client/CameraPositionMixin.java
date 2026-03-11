package io.wifi.starrailexpress.mixin.client;

import io.wifi.starrailexpress.block.SecurityMonitorBlock;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraPositionMixin {


    @Shadow public abstract void setPosition(Vec3 vec3);

    @Shadow @Final private BlockPos.MutableBlockPos blockPosition;

//    @Inject(method = "setPosition(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
//    private void setPosition(Vec3 vec3, CallbackInfo ci) {
//
//        if (CameraViewHandler.isInSecurityMode()) {
//            BlockPos cameraPos = CameraViewHandler.getCurrentCameraPos();
//            if (cameraPos != null) {
//                // 将相机位置设置为摄像头位置
//                this.blockPosition.set(cameraPos.getX() + 0.5, cameraPos.getY() - 1.2, cameraPos.getZ() + 0.5);
//                ci.cancel();
//
//
//            }
//        }
//    }

    @Inject(method = "setup", at = @At(value = "INVOKE",
            shift = At.Shift.BEFORE,
            target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"),
            cancellable = true)
    public void setupCamera(BlockGetter blockGetter, Entity entity, boolean bl, boolean bl2, float f, CallbackInfo ci) {
        if (SecurityMonitorBlock.setupCameraMod((Camera) (Object) this, blockGetter, entity, bl, bl2, f)) {
            ci.cancel();
        }
    }

}
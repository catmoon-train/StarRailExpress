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

package io.wifi.starrailexpress.mixin.client;

import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 桥接 mixin：把 Minecraft 的 {@link Camera#setup} 接到 {@link AdvancedCameraDirector}。
 *
 * <p>业务逻辑全部在 {@code net.exmo.sre.camera}，这里只在高级相机激活时用导演给出的位置/朝向覆盖相机，
 * 并取消原版的视角计算。安全摄像头由 {@code CameraPositionMixin} 单独处理且优先级更高，
 * 导演在 {@link AdvancedCameraDirector#shouldOverride()} 中已自动让位。
 */
@Mixin(Camera.class)
public abstract class AdvancedCameraMixin {

    @Shadow
    public abstract void setPosition(Vec3 vec3);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At(value = "INVOKE",
            shift = At.Shift.BEFORE,
            target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"),
            cancellable = true)
    public void sre$advancedCameraSetup(BlockGetter blockGetter, Entity entity, boolean detached,
                                        boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (AdvancedCameraDirector.shouldOverride()) {
            setRotation(AdvancedCameraDirector.getYaw(partialTick), AdvancedCameraDirector.getPitch(partialTick));
            setPosition(AdvancedCameraDirector.getCameraPos(partialTick));
            ci.cancel();
        }
    }
}

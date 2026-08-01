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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.gui.ScopeOverlayRenderer;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow
    @Final
    Minecraft minecraft;

    @WrapOperation(method = "shouldRenderBlockOutline", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;mayBuild:Z"))
    public boolean useOnBlock(Abilities instance, Operation<Boolean> original) {
        if (this.minecraft.getCameraEntity() instanceof LivingEntity entity && entity.getMainHandItem().getItem() instanceof AdventureUsable)
            return true;
        return original.call(instance);
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    public void modifyFov(net.minecraft.client.Camera camera, float partialTick, boolean bobbing, CallbackInfoReturnable<Double> cir) {
        if (ScopeOverlayRenderer.isInScopeView()) {
            double original = cir.getReturnValue();
            cir.setReturnValue(original / 3d); // 开镜时将FOV缩小到原来的1/3，实现拉近视角效果
            return;
        }
        // 高级相机轨道的 FOV 覆盖（开镜优先级更高，故放在其后）。
        float advancedFov = net.exmo.sre.camera.client.AdvancedCameraDirector.getFovOverride(partialTick);
        if (advancedFov > 0f) {
            cir.setReturnValue((double) advancedFov);
        }
    }
}
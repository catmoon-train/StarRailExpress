package io.wifi.starrailexpress.mixin.client.scenery;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.awt.*;

@Mixin(FogRenderer.class)
public class BackgroundRendererMixin {
    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 tmm$overrideFogColor(Vec3 pos, CubicSampler.Vec3Fetcher rgbFetcher, Operation<Vec3> original, @Local(argsOnly = true) ClientLevel world) {
        if (SREClient.isTrainMoving() && world.getDayTime() == 18000) {
            Color color = new Color(0xE406060B, true);
            return new Vec3(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f);
        }

        return original.call(pos, rgbFetcher);
    }
}

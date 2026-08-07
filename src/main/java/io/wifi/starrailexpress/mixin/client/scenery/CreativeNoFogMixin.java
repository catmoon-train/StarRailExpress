package io.wifi.starrailexpress.mixin.client.scenery;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.FogRenderer.FogMode;

@Mixin(FogRenderer.class)
public class CreativeNoFogMixin {
    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private static void onSetupFog(Camera camera, FogMode fogMode, float viewDistance, boolean thickFog,
            float tickDelta, CallbackInfo ci) {
        if (SREClient.isPlayerCreative() && SREClientConfig.instance().creativeNoFog) {
            // 取消原版雾设置
            ci.cancel();
            // 手动设置雾为“无雾”：距离极大，颜色沿用当前水平
            // FogRenderer.levelFogColor(); // 设置颜色为当前维度颜色
            RenderSystem.setShaderFogStart(800); // 或一个极大值，比如 100000
            RenderSystem.setShaderFogEnd(800);
            RenderSystem.setShaderFogShape(FogShape.CYLINDER);
        }
    }

}
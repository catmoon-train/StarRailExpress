package org.agmas.noellesroles.mixin.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 视野迷雾效果的渲染实现：在 {@link FogRenderer#setupFog} 末尾覆写着色器雾距，
 * 把拥有 {@link ModEffects#VISION_FOG} 效果的本地玩家的可见距离压缩成浓雾。
 *
 * <p>等级越高雾越远（看得越远），1 级（amplifier 0）时雾仅 2 格，见
 * {@link ModEffects#getVisionFogDistance(int)}。在 TAIL 覆写，确保压过原版雾距；
 * 当其它逻辑（如里世界迷雾 {@code WorldRendererMixin}）整体接管雾渲染、跳过
 * 原版 setupFog 时，本注入不触发，让位于那套更强的场景雾。</p>
 */
@Mixin(FogRenderer.class)
public class VisionFogMixin {

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V", at = @At("TAIL"))
    private static void noellesroles$applyVisionFog(Camera camera, FogRenderer.FogMode fogMode, float farPlaneDistance,
            boolean shouldRenderFog, float partialTick, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        MobEffectInstance instance = player.getEffect(ModEffects.VISION_FOG);
        if (instance == null) {
            return;
        }
        float end = ModEffects.getVisionFogDistance(instance.getAmplifier());
        float start = Math.max(0.0f, end - 2.0f);
        RenderSystem.setShaderFogStart(start);
        RenderSystem.setShaderFogEnd(end);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
}

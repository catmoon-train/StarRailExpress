package org.agmas.noellesroles.mixin.halloween.ambience;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.agmas.noellesroles.game.halloween.ambience.HalloweenAmbienceSky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 保留雨天透明度；原版在月亮绘制后恢复 shaderColor，不向其它绘制泄漏。 */
@Mixin(LevelRenderer.class)
public abstract class HalloweenSkyMoonMixin {
    @Inject(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMoonPhase()I"))
    private void halloween$orangeMoon(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Camera camera = client.gameRenderer.getMainCamera();
        if (HalloweenAmbienceSky.fogActive(camera, client.level)) {
            float[] color = RenderSystem.getShaderColor();
            RenderSystem.setShaderColor(color[0], color[1] * .675F, color[2] * .54F, color[3]);
        }
    }
}

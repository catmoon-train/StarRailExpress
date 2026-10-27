package org.agmas.noellesroles.mixin.client.roles.command_block;

import org.agmas.noellesroles.client.CommandBlockSkyLight;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 月亮四边形绘制前套上同一套紫，避免夜里留下一块白月亮。 */
@Mixin(LevelRenderer.class)
public abstract class CommandBlockSkyMoonMixin {
    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMoonPhase()I"))
    private void nr$purpleMoon(Matrix4f modelView, Matrix4f projection, float partialTick, Camera camera,
            boolean foggy, Runnable fogSetup, CallbackInfo ci) {
        ClientLevel level = camera.getEntity().level() instanceof ClientLevel client ? client : null;
        if (!CommandBlockSkyLight.overworldSky(level) || !CommandBlockSkyLight.active()) {
            return;
        }
        Vector3f color = CommandBlockSkyLight.moonColor();
        RenderSystem.setShaderColor(color.x(), color.y(), color.z(), 1.0F);
    }
}

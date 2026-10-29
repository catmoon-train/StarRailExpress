package org.agmas.noellesroles.mixin.client.roles.command_block;

import org.agmas.noellesroles.client.CommandBlockSkyLight;
import org.agmas.noellesroles.client.YouluFreeCamClient;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * 雾色跟天空穹顶走同一抹紫。清屏色在写入时染色，避免用上一帧的雾。
 * 幽露黑雾在方法尾部覆盖，这里先让开。
 */
@Mixin(value = FogRenderer.class, priority = 1100)
public abstract class CommandBlockSkyFogMixin {
    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V"))
    private static void nr$purpleClear(float red, float green, float blue, float alpha, Operation<Void> original,
            @Local(argsOnly = true) Camera camera, @Local(argsOnly = true) ClientLevel level) {
        if (!canTint(camera, level)) {
            original.call(red, green, blue, alpha);
            return;
        }
        float[] tinted = new float[3];
        CommandBlockSkyLight.tintFog(red, green, blue, tinted);
        original.call(tinted[0], tinted[1], tinted[2], alpha);
    }

    @Inject(method = "levelFogColor", at = @At("TAIL"))
    private static void nr$purpleFog(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!canTint(camera, minecraft.level)) {
            return;
        }
        float[] fog = RenderSystem.getShaderFogColor();
        float[] tinted = new float[3];
        CommandBlockSkyLight.tintFog(fog[0], fog[1], fog[2], tinted);
        RenderSystem.setShaderFogColor(tinted[0], tinted[1], tinted[2]);
    }

    private static boolean canTint(Camera camera, ClientLevel level) {
        return CommandBlockSkyLight.active()
                && CommandBlockSkyLight.overworldSky(level)
                && camera.getFluidInCamera() == FogType.NONE
                && !YouluFreeCamClient.shouldBlackenFog();
    }
}

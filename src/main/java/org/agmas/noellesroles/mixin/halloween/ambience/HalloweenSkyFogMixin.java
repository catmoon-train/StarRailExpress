package org.agmas.noellesroles.mixin.halloween.ambience;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.agmas.noellesroles.game.halloween.ambience.HalloweenAmbienceSky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 清屏色与实际雾色同步染色；不改雾距离，液体和高优先黑雾保留。 */
@Mixin(value = FogRenderer.class, priority = 1100)
public abstract class HalloweenSkyFogMixin {
    @WrapOperation(method = "setupColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V"))
    private static void halloween$orangeClear(float red, float green, float blue, float alpha,
            Operation<Void> original, @Local(argsOnly = true) Camera camera,
            @Local(argsOnly = true) ClientLevel level) {
        if (HalloweenAmbienceSky.fogActive(camera, level)) {
            float[] tint = HalloweenAmbienceSky.tintFog(red, green, blue);
            original.call(tint[0], tint[1], tint[2], alpha);
        } else {
            original.call(red, green, blue, alpha);
        }
    }

    @Inject(method = "levelFogColor()V", at = @At("TAIL"))
    private static void halloween$orangeFog(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (HalloweenAmbienceSky.fogActive(client.gameRenderer.getMainCamera(), client.level)) {
            float[] fog = RenderSystem.getShaderFogColor();
            float[] tint = HalloweenAmbienceSky.tintFog(fog[0], fog[1], fog[2]);
            RenderSystem.setShaderFogColor(tint[0], tint[1], tint[2]);
        }
    }
}

package io.wifi.starrailexpress.mixin.client.scenery;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.cca.StarTrainWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.util.AlwaysVisibleFrustum;
import net.minecraft.client.Camera;

import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin {

    @Inject(method = "offsetFrustum", at = @At(value = "RETURN"), cancellable = true)
    private static void tmm$setFrustumToAlwaysVisible(Frustum frustum, @NotNull CallbackInfoReturnable<Frustum> cir) {
        if (SREClient.isInLobby) {
            return;
        }
        cir.setReturnValue(new AlwaysVisibleFrustum(frustum));
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V"))
    public void tmm$disableSky(LevelRenderer instance, Matrix4f matrix4f, Matrix4f projectionMatrix, float tickDelta,
            Camera camera, boolean thickFog, Runnable fogCallback, Operation<Void> original) {
        if (!SREClient.isTrainMoving()
                )
            original.call(instance, matrix4f, projectionMatrix, tickDelta, camera, thickFog, fogCallback);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FogRenderer;setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V"))
    public void tmm$applyBlizzardFog(Camera camera, FogRenderer.FogMode fogType, float viewDistance, boolean thickFog,
            float tickDelta, Operation<Void> original) {
        if (SREClient.isInLobby) {
            original.call(camera, fogType, viewDistance, thickFog, tickDelta);
            return;
        }
        if (SREClient.trainComponent != null && SREClient.trainComponent.isFoggy()) {
            if (SREClient.isTrainMoving()) {
                tmm$doFog(0, 100);
            } else {
                tmm$doFog(0, 200);
            }
        }
    }

    @Unique
    private static void tmm$doFog(float fogStart, float fogEnd) {
        FogRenderer.FogData fogData = new FogRenderer.FogData(FogRenderer.FogMode.FOG_SKY);

        fogData.start = fogStart;
        fogData.end = fogEnd;

        fogData.shape = FogShape.SPHERE;

        RenderSystem.setShaderFogStart(fogData.start);
        RenderSystem.setShaderFogEnd(fogData.end);
        RenderSystem.setShaderFogShape(fogData.shape);
    }

}
package io.wifi.starrailexpress.mixin.client.scenery;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.init.ModEffects;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin {

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
        if (SREClient.trainComponent != null && SREClient.trainComponent.isFoggy()
                && SREClient.areaComponent != null && SREClient.areaComponent.fogEnabled
                && SREClient.isTrainMoving()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player.hasEffect(ModEffects.OTHERWORLD_AURA)){
                if (SREClient.gameComponent== null|| !SREClient.gameComponent.canUseKillerFeatures(player)){
                    tmm$doFog(0, 17);
                    return;
                }
            }
            if (player.hasEffect(MobEffects.BLINDNESS)){
                if (player.hasEffect(ModEffects.TAROT_ASSEMBLY)){
                    tmm$doFog(0, 20);
                    return;
                }
                tmm$doFog(0, 7);
                return;
            }
            // 如果地图未自定义 fogEnd（仍是默认200），则使用原默认值100
            tmm$doFog(0, SREClient.areaComponent.fogEnd != 200.0f ? SREClient.areaComponent.fogEnd : 100,
                    SREClient.areaComponent.fogShape);
        } else {
            original.call(camera, fogType, viewDistance, thickFog, tickDelta);
        }
    }

    @Unique
    private static void tmm$doFog(float fogStart, float fogEnd) {
        tmm$doFog(fogStart, fogEnd, "SPHERE");
    }

    @Unique
    private static void tmm$doFog(float fogStart, float fogEnd, String fogShapeStr) {
        FogRenderer.FogData fogData = new FogRenderer.FogData(FogRenderer.FogMode.FOG_SKY);

        fogData.start = fogStart;
        fogData.end = fogEnd;

        FogShape shape = FogShape.SPHERE;
        if ("CYLINDER".equalsIgnoreCase(fogShapeStr)) {
            shape = FogShape.CYLINDER;
        }
        fogData.shape = shape;

        RenderSystem.setShaderFogStart(fogData.start);
        RenderSystem.setShaderFogEnd(fogData.end);
        RenderSystem.setShaderFogShape(fogData.shape);
    }

}

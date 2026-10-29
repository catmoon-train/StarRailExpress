package org.agmas.noellesroles.mixin.halloween.ambience;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import org.agmas.noellesroles.game.halloween.ambience.HalloweenAmbienceSky;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 只包住第一个天光 lerp，不干预后续真实黑暗或亡魂夜像素处理。 */
@Mixin(LightTexture.class)
public abstract class HalloweenSkyLightMixin {
    @WrapOperation(method = "updateLightTexture(F)V", at = @At(value = "INVOKE",
            target = "Lorg/joml/Vector3f;lerp(Lorg/joml/Vector3fc;F)Lorg/joml/Vector3f;", ordinal = 0))
    private Vector3f halloween$warmSkylight(Vector3f instance, Vector3fc other, float amount,
            Operation<Vector3f> original) {
        Vector3f sky = original.call(instance, other, amount);
        if (HalloweenAmbienceSky.active(Minecraft.getInstance().level)) HalloweenAmbienceSky.tintSkyLight(sky);
        return sky;
    }
}

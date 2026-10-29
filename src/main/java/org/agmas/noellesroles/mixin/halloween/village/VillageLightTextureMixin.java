package org.agmas.noellesroles.mixin.halloween.village;

import net.minecraft.client.renderer.LightTexture;
import org.agmas.noellesroles.game.halloween.HalloweenVillageClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 客户端光照闪烁不写方块状态，不会在停局、断线或崩溃后损坏地图灯。 */
@Mixin(LightTexture.class)
public abstract class VillageLightTextureMixin {
    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"), index = 2)
    private int halloween$shade(int color) { return HalloweenVillageClient.shadeLightmap(color); }
}

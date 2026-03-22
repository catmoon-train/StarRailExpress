package io.wifi.starrailexpress.mixin.compat.sodium;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SkullBlock.Types;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(SkullBlockRenderer.class)
public class PlayerHeadFixerMixin {

    @Shadow
    @Final
    private static Map<SkullBlock.Type, ResourceLocation> SKIN_BY_TYPE;

    @Inject(method = "getRenderType", at = @At(value = "HEAD"), cancellable = true)
    private static void sre$getRenderType(SkullBlock.Type type, ResolvableProfile resolvableProfile,
            CallbackInfoReturnable<RenderType> cir) {
        try {
            ResourceLocation resourceLocation = (ResourceLocation) SKIN_BY_TYPE.get(type);
            if (type == Types.PLAYER && resolvableProfile != null) {
                SkinManager skinManager = Minecraft.getInstance().getSkinManager();
                cir.setReturnValue(RenderType
                        .entityTranslucent(skinManager.getInsecureSkin(resolvableProfile.gameProfile()).texture()));
            } else {
                cir.setReturnValue(RenderType.entityCutoutNoCullZOffset(resourceLocation));
            }
            cir.cancel();
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
        // return playerInfo.getSkin();
    }
}

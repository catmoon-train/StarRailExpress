package org.agmas.noellesroles.mixin.client.roles.priest;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.agmas.noellesroles.client.PriestHeavenClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 俯视镜头期间取消第一人称手臂，和银翼小鸟同一套拦截点。
 */
@Mixin(ItemInHandRenderer.class)
public class PriestHeavenHandMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void noellesroles$hideHandsDuringHeaven(float partialTick, PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource, LocalPlayer player, int packedLight, CallbackInfo ci) {
        if (PriestHeavenClient.isOverheadCamera()) {
            ci.cancel();
        }
    }
}

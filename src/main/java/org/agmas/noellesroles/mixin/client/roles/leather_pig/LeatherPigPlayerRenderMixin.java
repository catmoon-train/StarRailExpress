package org.agmas.noellesroles.mixin.client.roles.leather_pig;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.agmas.noellesroles.client.LeatherPigDisguiseRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class LeatherPigPlayerRenderMixin {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void noellesroles$renderLeatherPigAsPig(AbstractClientPlayer player, float yaw, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (!LeatherPigDisguiseRenderer.shouldDisguise(player)) {
            return;
        }
        if (LeatherPigDisguiseRenderer.render(player, yaw, tickDelta, poseStack, bufferSource, packedLight)) {
            ci.cancel();
        }
    }
}

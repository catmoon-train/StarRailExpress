package org.agmas.noellesroles.mixin.client.general;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.agmas.noellesroles.client.RouletteDevilClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 整体取消玩家渲染，装备、持物、名牌及直觉轮廓均不得泄露本体。 */
@Mixin(value = PlayerRenderer.class, priority = 2000)
public abstract class RouletteDevilVisibilityMixin {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void noellesroles$hideRouletteDevil(AbstractClientPlayer player, float yaw, float partialTick,
            PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (RouletteDevilClient.suppressPlayer(player)) ci.cancel();
    }
}

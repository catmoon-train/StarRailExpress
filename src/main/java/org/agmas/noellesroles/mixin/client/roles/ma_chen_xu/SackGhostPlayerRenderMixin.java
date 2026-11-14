package org.agmas.noellesroles.mixin.client.roles.ma_chen_xu;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.agmas.noellesroles.client.SackGhostRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在所有玩家层之前替换；护甲、披风、手持和名牌不会从隐身或鬼形里漏出。 */
@Mixin(value = PlayerRenderer.class, priority = 1900)
public abstract class SackGhostPlayerRenderMixin {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void sackGhost$replace(AbstractClientPlayer player, float yaw, float partial, PoseStack poses,
                                    MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (SackGhostRenderer.replace(player, partial, poses, buffers, light)) ci.cancel();
    }
}

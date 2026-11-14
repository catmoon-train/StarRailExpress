package org.agmas.noellesroles.mixin.client.roles.ma_chen_xu;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.agmas.noellesroles.client.SackGhostClient;
import org.agmas.noellesroles.client.SackGhostRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 第一人称同样隐藏整个物品/手臂入口，不能只把手持栈换成 EMPTY。 */
@Mixin(value = ItemInHandRenderer.class, priority = 1900)
public abstract class SackGhostHandRenderMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void sackGhost$hands(float partial, PoseStack poses, MultiBufferSource.BufferSource buffers,
                                 LocalPlayer player, int light, CallbackInfo ci) {
        int flags = SackGhostClient.formFlags(player);
        if (flags == 0) return;
        if ((flags & 3) != 0 && (flags & 4) == 0) SackGhostRenderer.drawFirstPerson(poses, buffers, partial);
        ci.cancel();
    }
}

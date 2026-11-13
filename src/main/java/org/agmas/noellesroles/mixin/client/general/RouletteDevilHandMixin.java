package org.agmas.noellesroles.mixin.client.general;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.agmas.noellesroles.client.RouletteDevilClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 第一人称也不显示手臂或装备，避免烟雾形态下保留普通玩家的持物外观。 */
@Mixin(value = ItemInHandRenderer.class, priority = 2000)
public abstract class RouletteDevilHandMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void noellesroles$hideRouletteDevilHands(float partialTick, PoseStack poses,
            MultiBufferSource.BufferSource buffers, LocalPlayer player, int light, CallbackInfo ci) {
        if (RouletteDevilClient.suppressPlayer(player)) ci.cancel();
    }
}

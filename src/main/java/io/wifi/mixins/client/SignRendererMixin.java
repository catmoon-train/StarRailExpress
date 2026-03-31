package io.wifi.mixins.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignText;

@Mixin(SignRenderer.class)
public class SignRendererMixin {
    @Unique
    private static final int SRE$MAX_DISTANCE = 10;

    @Inject(method = "renderSignText", at = @At("HEAD"), cancellable = true)
    private void sre$limitRenderSignText(BlockPos blockPos, SignText signText, PoseStack poseStack,
            MultiBufferSource multiBufferSource, int i, int j, int k, boolean bl, CallbackInfo ci) {
        final var client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (blockPos.distToCenterSqr(client.player.position()) >= SRE$MAX_DISTANCE) {
            ci.cancel();
            return;
        }
    }
}

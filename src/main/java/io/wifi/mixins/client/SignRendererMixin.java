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
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

@Mixin(SignRenderer.class)
public class SignRendererMixin {
    @Unique
    private static final int SRE$MAX_BLOCK_DISTANCE = 32 * 32;
    private static final int SRE$MAX_TEXT_DISTANCE = 16 * 16;

    @Inject(method = "renderSignText", at = @At("HEAD"), cancellable = true)
    private void sre$blockRenderSignText(BlockPos blockPos, SignText signText, PoseStack poseStack,
            MultiBufferSource multiBufferSource, int i, int j, int k, boolean bl, CallbackInfo ci) {
        final var client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (blockPos.distToCenterSqr(client.player.position()) >= SRE$MAX_TEXT_DISTANCE) {
            ci.cancel();
            return;
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void sre$blockRenderSign(SignBlockEntity signBlockEntity, float f, PoseStack poseStack,
            MultiBufferSource multiBufferSource, int i, int j, CallbackInfo ci) {
        final var client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (signBlockEntity.getBlockPos().distToCenterSqr(client.player.position()) >= SRE$MAX_BLOCK_DISTANCE) {
            ci.cancel();
            return;
        }
    }
}

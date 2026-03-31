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
import net.minecraft.world.level.block.entity.SignBlockEntity;

@Mixin(SignRenderer.class)
public class SignRendererMixin {
    @Unique
    private static final int SRE$MAX_DISTANCE = 10 * 10;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void render(SignBlockEntity signBlockEntity, float f, PoseStack poseStack,
            MultiBufferSource multiBufferSource, int i, int j, CallbackInfo ci) {
        final var client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (signBlockEntity.getBlockPos().distToCenterSqr(client.player.position()) >= SRE$MAX_DISTANCE) {
            ci.cancel();
            return;
        }
    }
}

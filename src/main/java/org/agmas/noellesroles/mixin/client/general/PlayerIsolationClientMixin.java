package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class PlayerIsolationClientMixin {
    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void hideName(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.player.hasEffect(ModEffects.PLAYER_ISOLATION)) return;
        if (entity instanceof Player && entity != mc.player) cir.setReturnValue(false);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hideModel(LivingEntity entity, float entityYaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.player.hasEffect(ModEffects.PLAYER_ISOLATION)) return;
        if (entity instanceof Player && entity != mc.player) ci.cancel();
    }
}

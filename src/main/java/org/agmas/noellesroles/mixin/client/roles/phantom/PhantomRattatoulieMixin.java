package org.agmas.noellesroles.mixin.client.roles.phantom;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.doctor4t.ratatouille.client.render.feature.PlushOnHeadFeatureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlushOnHeadFeatureRenderer.class)
public abstract class PhantomRattatoulieMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    public void phantomRatMixin(PoseStack matrices, MultiBufferSource vertexConsumerProvider, int i, LivingEntity livingEntity, float f, float g, float h, float j, float k, float l, CallbackInfo ci) {
       if (livingEntity.isInvisible()) ci.cancel();
    }
}

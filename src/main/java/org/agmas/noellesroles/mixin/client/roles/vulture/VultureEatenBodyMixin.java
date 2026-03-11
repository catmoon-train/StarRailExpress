package org.agmas.noellesroles.mixin.client.roles.vulture;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.render.entity.PlayerBodyEntityRenderer;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import org.agmas.noellesroles.roles.coroner.BodyDeathReasonComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerBodyEntityRenderer.class)
public abstract class VultureEatenBodyMixin {

    @Inject(method = "renderBody", at = @At("TAIL"), cancellable = true)
    public void vultureSkeletonOnly(PlayerBodyEntity livingEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int light, float alpha, CallbackInfo ci) {
        BodyDeathReasonComponent bodyDeathReasonComponent = BodyDeathReasonComponent.KEY.get(livingEntity);
        if (bodyDeathReasonComponent.vultured) {
            ci.cancel();
        }
    }
}

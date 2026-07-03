package org.agmas.noellesroles.mixin.client.roles.salted_fish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.agmas.noellesroles.game.roles.innocence.salted_fish.SaltedFishPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class SaltedFishRenderMixin
        extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public SaltedFishRenderMixin(EntityRendererProvider.Context context,
            PlayerModel<AbstractClientPlayer> entityModel, float shadowRadius) {
        super(context, entityModel, shadowRadius);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void noellesroles$saltedFishBeforeRender(AbstractClientPlayer player, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        SaltedFishPlayerComponent comp = SaltedFishPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (comp == null || !comp.isActive() || player.isSpectator()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.72D + comp.getRenderBounce(partialTick), 0.0D);
        poseStack.mulPose(Axis.ZP.rotationDegrees(comp.getRenderRoll(partialTick)));
        poseStack.translate(0.0D, -0.72D, 0.0D);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void noellesroles$saltedFishAfterRender(AbstractClientPlayer player, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        SaltedFishPlayerComponent comp = SaltedFishPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (comp == null || !comp.isActive() || player.isSpectator()) {
            return;
        }
        poseStack.popPose();
    }
}

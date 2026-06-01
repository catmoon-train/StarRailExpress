package io.wifi.starrailexpress.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.render.entity.EmojiHelmetRenderer;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.entity.layers.CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin<T extends LivingEntity, M extends EntityModel<T> & HeadedModel>
        extends RenderLayer<T, M> {
    public CustomHeadLayerMixin(RenderLayerParent<T, M> renderLayerParent) {
        super(renderLayerParent);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void sre$renderEmojiHelmetOnFace(PoseStack poseStack, MultiBufferSource bufferSource, int light,
            T livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        ItemStack stack = livingEntity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (!stack.is(TMMItems.EMOJI_HELMET)) {
            return;
        }

        ci.cancel();
        if (!(livingEntity instanceof Player player) || player.isInvisible()) {
            return;
        }

        poseStack.pushPose();
        this.getParentModel().getHead().translateAndRotate(poseStack);
        EmojiHelmetRenderer.renderOnFace(stack, poseStack, bufferSource);
        poseStack.popPose();
    }
}

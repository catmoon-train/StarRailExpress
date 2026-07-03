package org.agmas.noellesroles.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.C4ModelTransforms;
import org.agmas.noellesroles.game.c4.C4PlacementPreset;
import org.agmas.noellesroles.init.ModItems;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class C4ItemEntityRendererMixin {
    @Shadow @Final private ItemRenderer itemRenderer;

    @Inject(
        method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void renderC4AsPlantedCharge(ItemEntity entity, float yaw, float tickDelta,
            PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        ItemStack stack = entity.getItem();
        if (!stack.is(ModItems.C4)) return;

        matrices.pushPose();
        if (entity.isNoGravity()) {
            C4ModelTransforms.rotateFrontToSurface(matrices, entity.getViewYRot(tickDelta), entity.getViewXRot(tickDelta));
        } else {
            rotateToVelocity(matrices, entity, tickDelta);
        }
        if (entity.isNoGravity()) {
            C4ModelTransforms.applySurfacePlacement(matrices, C4PlacementPreset.DEFAULT);
        } else {
            C4ModelTransforms.applyPlacement(matrices, C4PlacementPreset.DEFAULT);
        }
        this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light,
            OverlayTexture.NO_OVERLAY, matrices, vertexConsumers, entity.level(), entity.getId());
        matrices.popPose();
        ci.cancel();
    }

    @Unique
    private static void rotateToVelocity(PoseStack matrices, ItemEntity entity, float tickDelta) {
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() <= 1.0E-6D) {
            C4ModelTransforms.rotateToSurface(matrices, entity.getViewYRot(tickDelta), entity.getViewXRot(tickDelta));
            return;
        }
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float flightYaw = (float) (Mth.atan2(velocity.x, velocity.z) * Mth.RAD_TO_DEG);
        float flightPitch = (float) (-Mth.atan2(velocity.y, horizontal) * Mth.RAD_TO_DEG);
        C4ModelTransforms.rotateToSurface(matrices, flightYaw, flightPitch);
    }
}

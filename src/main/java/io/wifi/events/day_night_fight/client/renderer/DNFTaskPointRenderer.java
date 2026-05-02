package io.wifi.events.day_night_fight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.events.day_night_fight.DNFItems;
import io.wifi.events.day_night_fight.block.DNFTaskPointBlock;
import io.wifi.events.day_night_fight.entity.DNFTaskPointEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class DNFTaskPointRenderer extends EntityRenderer<DNFTaskPointEntity> {
    private final ItemRenderer itemRenderer;

    public DNFTaskPointRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(DNFTaskPointEntity entity, float yaw, float tickDelta, PoseStack poseStack,
            MultiBufferSource multiBufferSource, int light) {
        poseStack.pushPose();
        float age = entity.tickCount + tickDelta;
        poseStack.translate(0, Math.sin(age * 0.08f) * 0.08f, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 3.0f));
        poseStack.scale(0.7f, 0.7f, 0.7f);
        ItemStack stack = getDisplayStack(entity);
        itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                poseStack, multiBufferSource, entity.level(), entity.getId());
        poseStack.popPose();
        super.render(entity, yaw, tickDelta, poseStack, multiBufferSource, light);
    }

    private ItemStack getDisplayStack(DNFTaskPointEntity entity) {
        if (entity.level().getBlockState(entity.getSourcePos()).getBlock() instanceof DNFTaskPointBlock block
                && block.getTaskPointType() == DNFTaskPointBlock.TaskPointType.EXCHANGE) {
            return new ItemStack(Items.EMERALD);
        }
        return DNFItems.CLEANING_BYPRODUCT.getDefaultInstance();
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(DNFTaskPointEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

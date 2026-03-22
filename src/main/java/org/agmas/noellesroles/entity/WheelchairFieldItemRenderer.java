package org.agmas.noellesroles.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * 场地道具实体渲染器 - 使用旋转浮动的物品展示
 */
public class WheelchairFieldItemRenderer extends EntityRenderer<WheelchairFieldItemEntity> {

    private final ItemRenderer itemRenderer;

    public WheelchairFieldItemRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.25f;
    }

    @Override
    public void render(WheelchairFieldItemEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (entity.isInvisible()) {
            return;
        }

        poseStack.pushPose();

        // 浮动效果
        float bobOffset = (float) Math.sin((entity.tickCount + partialTick) * 0.1) * 0.1f;
        poseStack.translate(0.0, 0.25 + bobOffset, 0.0);

        // 旋转效果
        float rotation = (entity.tickCount + partialTick) * 3.0f;
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));

        // 稍微放大显示
        poseStack.scale(1.2f, 1.2f, 1.2f);

        this.itemRenderer.renderStatic(
                entity.getItem(), ItemDisplayContext.GROUND,
                packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, bufferSource,
                entity.level(), entity.getId());

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(WheelchairFieldItemEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

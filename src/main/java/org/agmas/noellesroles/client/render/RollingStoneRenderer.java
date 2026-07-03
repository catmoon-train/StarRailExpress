package org.agmas.noellesroles.client.render;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.agmas.noellesroles.content.entity.RollingStoneEntity;

/**
 * 滚石渲染：以原版石头方块渲染，随移动方向滚动。
 */
public class RollingStoneRenderer extends EntityRenderer<RollingStoneEntity> {

    private final ItemRenderer itemRenderer;
    private final ItemStack stone = new ItemStack(Blocks.STONE);

    public RollingStoneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(RollingStoneEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource buffers, int light) {
        matrices.pushPose();
        matrices.translate(0.0, 0.9, 0.0);
        float roll = (entity.tickCount + tickDelta) * 18.0F;
        matrices.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        matrices.mulPose(Axis.XP.rotationDegrees(roll));
        matrices.scale(1.9F, 1.9F, 1.9F);
        this.itemRenderer.renderStatic(this.stone, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                matrices, buffers, entity.level(), entity.getId());
        matrices.popPose();
        super.render(entity, yaw, tickDelta, matrices, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(RollingStoneEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

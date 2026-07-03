package org.agmas.noellesroles.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
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
import org.agmas.noellesroles.content.entity.MovingPlatformEntity;

/**
 * 移动平台渲染：以原版平滑石头方块渲染（约 1×0.6×1）。
 */
public class MovingPlatformRenderer extends EntityRenderer<MovingPlatformEntity> {

    private final ItemRenderer itemRenderer;
    private final ItemStack block = new ItemStack(Blocks.SMOOTH_STONE);

    public MovingPlatformRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(MovingPlatformEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource buffers, int light) {
        matrices.pushPose();
        matrices.translate(0.0, 0.3, 0.0);
        matrices.scale(2.0F, 0.6F, 2.0F);
        this.itemRenderer.renderStatic(this.block, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                matrices, buffers, entity.level(), entity.getId());
        matrices.popPose();
        super.render(entity, yaw, tickDelta, matrices, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MovingPlatformEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

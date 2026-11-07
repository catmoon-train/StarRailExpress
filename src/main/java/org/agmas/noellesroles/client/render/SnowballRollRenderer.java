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
import org.agmas.noellesroles.content.entity.SnowballRollEntity;

/**
 * 雪球渲染：用原版雪块画一颗球，尺寸与滚动角度都由实体同步。
 *
 * <p>
 * 尺寸来自 {@link SnowballRollEntity#getSize()}（0.5 ~ 6），渲染时按尺寸线性缩放方块，
 * 于是视觉大小和碰撞箱（{@code getDimensions}）保持一致。
 * 滚动角度用实体同步的 {@link SnowballRollEntity#getRoll()} 而不是本地 {@code tickCount}，
 * 这样雪球停下时不会继续空转、被推出时也能对上速度。
 */
public class SnowballRollRenderer extends EntityRenderer<SnowballRollEntity> {

    private final ItemRenderer itemRenderer;
    private final ItemStack snow = new ItemStack(Blocks.SNOW_BLOCK);

    public SnowballRollRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(SnowballRollEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource buffers, int light) {
        float size = entity.getSize();
        matrices.pushPose();
        // 方块中心在 y=0.5，抬到实体中心高度（半径 = size / 2）让球贴地滚动
        matrices.translate(0.0, size * 0.5, 0.0);
        matrices.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        matrices.mulPose(Axis.XP.rotationDegrees(entity.getRoll()));
        matrices.scale(size, size, size);
        this.itemRenderer.renderStatic(this.snow, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                matrices, buffers, entity.level(), entity.getId());
        matrices.popPose();
        super.render(entity, yaw, tickDelta, matrices, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SnowballRollEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

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
import org.agmas.noellesroles.content.entity.SteelCoilEntity;
import org.agmas.noellesroles.init.ModItems;

/**
 * 钢卷渲染：用 Blockbench 导出的 12 边形钢卷模型绘制，绕与前进方向垂直的轴滚动。
 */
public class SteelCoilRenderer extends EntityRenderer<SteelCoilEntity> {

    private final ItemRenderer itemRenderer;
    private final ItemStack coil;

    public SteelCoilRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.coil = new ItemStack(ModItems.STEEL_COIL);
    }

    @Override
    public void render(SteelCoilEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource buffers, int light) {
        matrices.pushPose();
        matrices.translate(0.0, SteelCoilEntity.SIZE * 0.5, 0.0);
        // 模型圆柱轴沿 X：先转到与前进方向垂直，再绕该轴滚动
        matrices.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        matrices.mulPose(Axis.XP.rotationDegrees(entity.getRoll() + tickDelta * entity.getRollSpeed() * 57.2958F));
        matrices.scale(SteelCoilEntity.SIZE, SteelCoilEntity.SIZE, SteelCoilEntity.SIZE);
        this.itemRenderer.renderStatic(this.coil, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                matrices, buffers, entity.level(), entity.getId());
        matrices.popPose();
        super.render(entity, yaw, tickDelta, matrices, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SteelCoilEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

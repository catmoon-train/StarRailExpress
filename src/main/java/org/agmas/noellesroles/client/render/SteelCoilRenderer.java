package org.agmas.noellesroles.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.agmas.noellesroles.client.SteelCoilModelPlugin;
import org.agmas.noellesroles.content.entity.SteelCoilEntity;

import java.util.List;

/**
 * 钢卷渲染：完全走原版方块模型烘焙。
 *
 * <p>模型是 {@code assets/noellesroles/models/entity/steel_coil.json} 里的 16 边形圆柱，
 * 每片 22.5°，每个 element 的旋转角都取原版允许的 0 / ±22.5 / ±45；顶点与 UV 由
 * {@code FaceBakery} 按模型数据和图集坐标计算，渲染器只负责摆放与滚动，不再手写任何四边形的贴图对应。
 *
 * <p>烘焙后的顶点位于 [0,1]³（模型 0..16 单位除以 16），这里按实体声明的 SIZE 缩放并居中：
 * 轴向另按模型实际宽度换算，保证显示宽度 = SIZE，截面直径也 = SIZE，与碰撞箱和滚动半径一致。
 */
public class SteelCoilRenderer extends EntityRenderer<SteelCoilEntity> {

    /** 模型轴向宽度（像素）：模型 x 方向只占 2..14。 */
    private static final float MODEL_WIDTH = 12.0F;
    /** 模型截面跨度（像素）：y/z 方向占 0.4..15.6。 */
    private static final float MODEL_DIAMETER = 15.2F;

    private static final float SCALE_X = SteelCoilEntity.SIZE * 16.0F / MODEL_WIDTH;
    private static final float SCALE_R = SteelCoilEntity.SIZE * 16.0F / MODEL_DIAMETER;

    private static final RandomSource RANDOM = RandomSource.create(42L);

    public SteelCoilRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = SteelCoilEntity.SIZE * 0.5F;
    }

    private static BakedModel bakedModel() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getModelManager() == null) {
            return null;
        }
        BakedModel missing = minecraft.getModelManager().getMissingModel();
        // Fabric extra model 烘焙后挂在 "fabric_resource" 变体下
        for (String variant : new String[] { "fabric_resource", "standalone", "inventory" }) {
            BakedModel model = minecraft.getModelManager()
                    .getModel(new ModelResourceLocation(SteelCoilModelPlugin.MODEL_ID, variant));
            if (model != null && model != missing) {
                return model;
            }
        }
        return null;
    }

    @Override
    public void render(SteelCoilEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource buffers, int light) {
        BakedModel model = bakedModel();
        if (model != null) {
            matrices.pushPose();
            matrices.translate(0.0F, SteelCoilEntity.SIZE * 0.5F, 0.0F);
            matrices.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            matrices.mulPose(Axis.XP.rotationDegrees(
                    entity.getRoll() + tickDelta * entity.getRollSpeed() * 57.2958F));
            // 缩放先于旋转作用（局部空间），然后再把模型中心 (0.5,0.5,0.5) 挪到原点
            matrices.scale(SCALE_X, SCALE_R, SCALE_R);
            matrices.translate(-0.5F, -0.5F, -0.5F);
            PoseStack.Pose pose = matrices.last();
            VertexConsumer consumer = buffers
                    .getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
            for (Direction direction : Direction.values()) {
                emit(consumer, pose, model.getQuads(null, direction, RANDOM), light);
            }
            emit(consumer, pose, model.getQuads(null, null, RANDOM), light);
            matrices.popPose();
        }
        super.render(entity, yaw, tickDelta, matrices, buffers, light);
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, List<BakedQuad> quads, int light) {
        for (BakedQuad quad : quads) {
            consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(SteelCoilEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

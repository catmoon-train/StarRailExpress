package org.agmas.noellesroles.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.SteelCoilEntity;

/**
 * 钢卷渲染：自定义实体模型层，用 12 个绕 X 轴各转 30° 的长方体拼成 12 边形圆柱。
 *
 * <p>不走方块模型 JSON：MC 1.21.1 的方块模型只允许 0/±22.5/±45 度，
 * 无法表达 30° 面片；实体模型层用弧度制且按 16 单位/格缩放，不受此限制。
 */
public class SteelCoilRenderer extends EntityRenderer<SteelCoilEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Noellesroles.id("steel_coil"), "main");
    private static final ResourceLocation TEXTURE = Noellesroles.id("textures/block/steel_coil.png");

    private static final int SIDES = 12;
    /** 圆柱半径（模型单位，16 = 1 格）；半径 16 即直径 2 格，与钢卷尺寸一致。 */
    private static final float RADIUS = 16.0F;
    /** 圆柱轴向长度（模型单位），24 = 1.5 格。 */
    private static final float LENGTH = 24.0F;
    /** 每个面片的半宽（模型单位），略大于内切半宽以填满 12 边形。 */
    private static final float SEG_HALF = 4.4F;

    private final ModelPart root;

    public SteelCoilRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.root = ctx.bakeLayer(LAYER);
        this.shadowRadius = SteelCoilEntity.SIZE * 0.5F;
    }

    /** 注册模型层；供 {@code NoellesrolesClient} 调用。 */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 面片初始位于圆顶（+Y），pivot 在圆心，绕 X 轴按 30° 倍数旋转围成 12 边形
        for (int i = 0; i < SIDES; i++) {
            float xRot = i * (360.0F / SIDES) * Mth.DEG_TO_RAD;
            root.addOrReplaceChild("side_" + i,
                    CubeListBuilder.create().texOffs(0, 0)
                            .addBox(-LENGTH / 2.0F, RADIUS - SEG_HALF, -SEG_HALF,
                                    LENGTH, SEG_HALF * 2.0F, SEG_HALF * 2.0F,
                                    CubeDeformation.NONE),
                    PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, xRot, 0.0F, 0.0F));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void render(SteelCoilEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource buffers, int light) {
        matrices.pushPose();
        matrices.translate(0.0F, SteelCoilEntity.SIZE * 0.5F, 0.0F);
        // 圆柱轴沿 X：先转到与前进方向垂直，再绕该轴滚动
        matrices.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        matrices.mulPose(Axis.XP.rotationDegrees(entity.getRoll() + tickDelta * entity.getRollSpeed() * 57.2958F));

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        this.root.render(matrices, consumer, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();
        super.render(entity, yaw, tickDelta, matrices, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SteelCoilEntity entity) {
        return TEXTURE;
    }
}

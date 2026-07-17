package net.exmo.sre.sixtyseconds.client.render;

import net.exmo.sre.sixtyseconds.entity.OceanSeaMonsterEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 海怪盒子模型（128×128 贴图）。
 * 结构：球状躯体 + 大型眼睛 ×2 + 8 条触手 + 头部鳍/角。
 * <p>
 * 触手分四层环绕躯体分布，各有独立摆动动画（模拟游动/攻击）。
 * 模型空间 y=24 基线。
 */
public class OceanSeaMonsterModel extends EntityModel<OceanSeaMonsterEntity> {

    private static final int TEX_W = 128;
    private static final int TEX_H = 128;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart leftEye;
    private final ModelPart rightEye;
    private final ModelPart[] tentacles;

    public OceanSeaMonsterModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.leftEye = body.getChild("left_eye");
        this.rightEye = body.getChild("right_eye");
        this.tentacles = new ModelPart[8];
        for (int i = 0; i < 8; i++) {
            tentacles[i] = root.getChild("tentacle_" + i);
        }
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();

        // 主体（椭球）
        PartDefinition bodyDef = part.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-8.0F, -10.0F, -8.0F, 16.0F, 20.0F, 16.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        // 头顶冠/鳍
        bodyDef.addOrReplaceChild("crest",
                CubeListBuilder.create()
                        .texOffs(0, 38)
                        .addBox(-3.0F, -16.0F, 0.0F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // 左眼
        bodyDef.addOrReplaceChild("left_eye",
                CubeListBuilder.create()
                        .texOffs(68, 0)
                        .addBox(0.0F, -3.0F, -3.0F, 4.0F, 6.0F, 6.0F),
                PartPose.offset(8.0F, -4.0F, -5.0F));

        // 右眼
        bodyDef.addOrReplaceChild("right_eye",
                CubeListBuilder.create()
                        .texOffs(68, 0)
                        .addBox(-4.0F, -3.0F, -3.0F, 4.0F, 6.0F, 6.0F),
                PartPose.offset(-8.0F, -4.0F, -5.0F));

        // 8 条触手（绕躯体环状分布，每条 3 节）
        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI / 8) * i;
            float x = (float) (Math.cos(angle) * 6.0);
            float z = (float) (Math.sin(angle) * 6.0);
            PartDefinition tentDef = part.addOrReplaceChild("tentacle_" + i,
                    CubeListBuilder.create()
                            .texOffs(24, 56 + (i % 4) * 16),
                    PartPose.offsetAndRotation(x, 24.0F + 8.0F, z, 0.3F, (float) angle, 0.0F));

            // 第一节（粗）
            tentDef.addOrReplaceChild("seg1",
                    CubeListBuilder.create()
                            .texOffs(24, 56 + (i % 4) * 16)
                            .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F),
                    PartPose.offset(0.0F, 0.0F, 0.0F));

            // 第二节（中）
            tentDef.getChild("seg1").addOrReplaceChild("seg2",
                    CubeListBuilder.create()
                            .texOffs(40, 56 + (i % 4) * 16)
                            .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F),
                    PartPose.offset(0.0F, 8.0F, 0.0F));

            // 第三节（细尖）
            tentDef.getChild("seg1").getChild("seg2").addOrReplaceChild("seg3",
                    CubeListBuilder.create()
                            .texOffs(52, 56 + (i % 4) * 16)
                            .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                    PartPose.offset(0.0F, 7.0F, 0.0F));
        }

        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }

    @Override
    public void setupAnim(OceanSeaMonsterEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        // 身体上下微微浮动
        body.y = 24.0F + Mth.sin(ageInTicks * 0.08F) * 1.0F;

        // 眼睛跟随（发光感）
        float eyeGlow = Mth.sin(ageInTicks * 0.15F) * 0.05F;
        leftEye.zScale = 1.0F + eyeGlow;
        leftEye.xScale = 1.0F + eyeGlow;
        rightEye.zScale = 1.0F + eyeGlow;
        rightEye.xScale = 1.0F + eyeGlow;

        // 触手波浪摆动（每条相位不同）
        for (int i = 0; i < 8; i++) {
            float phase = i * 0.785F; // π/4
            float swing = Mth.sin(ageInTicks * 0.12F + phase) * 0.3F;
            float swing2 = Mth.cos(ageInTicks * 0.1F + phase) * 0.25F;
            tentacles[i].xRot = 0.3F + swing;
            tentacles[i].zRot = swing2;
            // 子节点传播摆动
            var seg1 = tentacles[i].getChild("seg1");
            if (seg1 != null) {
                seg1.xRot = swing * 0.5F;
                var seg2 = seg1.getChild("seg2");
                if (seg2 != null) {
                    seg2.xRot = swing * 0.3F;
                    var seg3 = seg2.getChild("seg3");
                    if (seg3 != null) {
                        seg3.xRot = swing * 0.2F;
                    }
                }
            }
        }

        // 受伤时全身抖动
        if (entity.hurtTime > 0) {
            float shake = Mth.sin(entity.hurtTime * 0.9F) * 0.08F;
            body.xRot = shake;
            body.zRot = shake;
        }
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer buffer, int packedLight,
            int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}

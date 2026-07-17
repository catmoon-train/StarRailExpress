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
 * 海怪盒子模型（128×128 贴图）。3 个海怪变体（克拉肯/海蛇/利维坦）共用本模型，只换贴图——
 * 头足类轮廓：椭球躯体 + 头冠 + 双眼 + 喙 + 8 条三节触手。
 * <p>
 * 8 条触手共享同一组 UV 矩形（它们外观一致，没必要各占一块贴图）。
 * 模型空间 y=24 基线；{@code texOffs}/尺寸与
 * {@code tools/gen_sixtyseconds_ocean_textures.py} 逐面上色的矩形一一对应——改模型须同步改脚本。
 *
 * <h3>UV 布局（128×128）</h3>
 * <pre>
 *   body    texOffs(0,0)    14×18×14
 *   crest   texOffs(0,34)   8×4×2
 *   eye     texOffs(60,0)   4×4×4   （左右共用）
 *   beak    texOffs(60,12)  6×3×3
 *   tent1   texOffs(0,44)   4×8×4   （8 条共用）
 *   tent2   texOffs(20,44)  3×7×3
 *   tent3   texOffs(36,44)  2×6×2
 * </pre>
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

        // 主体（椭球状套体）
        PartDefinition bodyDef = part.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-7.0F, -9.0F, -7.0F, 14.0F, 18.0F, 14.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        // 头顶冠/角（薄片）
        bodyDef.addOrReplaceChild("crest",
                CubeListBuilder.create().texOffs(0, 34)
                        .addBox(-4.0F, -4.0F, -1.0F, 8.0F, 4.0F, 2.0F),
                PartPose.offset(0.0F, -9.0F, 0.0F));

        // 双眼（朝 -Z 前方，向两侧张开）
        bodyDef.addOrReplaceChild("left_eye",
                CubeListBuilder.create().texOffs(60, 0)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(5.0F, -3.0F, -7.0F));
        bodyDef.addOrReplaceChild("right_eye",
                CubeListBuilder.create().texOffs(60, 0)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(-5.0F, -3.0F, -7.0F));

        // 喙/口器（腹侧前方）
        bodyDef.addOrReplaceChild("beak",
                CubeListBuilder.create().texOffs(60, 12)
                        .addBox(-3.0F, 0.0F, -2.0F, 6.0F, 3.0F, 3.0F),
                PartPose.offset(0.0F, 5.0F, -6.0F));

        // 8 条触手（绕躯体环状；每条 3 节，逐节收窄；8 条共用同一组 UV）
        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI / 8) * i;
            float x = (float) (Math.cos(angle) * 6.0);
            float z = (float) (Math.sin(angle) * 6.0);
            PartDefinition tentDef = part.addOrReplaceChild("tentacle_" + i,
                    CubeListBuilder.create(),
                    PartPose.offsetAndRotation(x, 24.0F + 7.0F, z, 0.3F, (float) angle, 0.0F));
            tentDef.addOrReplaceChild("seg1",
                    CubeListBuilder.create().texOffs(0, 44)
                            .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F),
                    PartPose.offset(0.0F, 0.0F, 0.0F));
            tentDef.getChild("seg1").addOrReplaceChild("seg2",
                    CubeListBuilder.create().texOffs(20, 44)
                            .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F),
                    PartPose.offset(0.0F, 8.0F, 0.0F));
            tentDef.getChild("seg1").getChild("seg2").addOrReplaceChild("seg3",
                    CubeListBuilder.create().texOffs(36, 44)
                            .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                    PartPose.offset(0.0F, 7.0F, 0.0F));
        }

        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }

    @Override
    public void setupAnim(OceanSeaMonsterEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        // 躯体上下浮动
        body.y = 24.0F + Mth.sin(ageInTicks * 0.08F) * 1.0F;

        // 眼睛脉动（发光感）
        float eyeGlow = Mth.sin(ageInTicks * 0.15F) * 0.05F;
        leftEye.zScale = leftEye.xScale = 1.0F + eyeGlow;
        rightEye.zScale = rightEye.xScale = 1.0F + eyeGlow;

        // 触手波浪摆动（每条相位错开，逐节递减传播）
        for (int i = 0; i < 8; i++) {
            float phase = i * 0.785F; // π/4
            float swing = Mth.sin(ageInTicks * 0.12F + phase) * 0.3F;
            float swing2 = Mth.cos(ageInTicks * 0.1F + phase) * 0.25F;
            tentacles[i].xRot = 0.3F + swing;
            tentacles[i].zRot = swing2;
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

        // 受伤全身抖动
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

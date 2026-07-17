package net.exmo.sre.sixtyseconds.client.render;

import net.exmo.sre.sixtyseconds.entity.OceanSharkEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 鲨鱼盒子模型（128×128 贴图）。
 * 结构：流线型身体 + 背鳍 + 胸鳍 ×2 + 尾鳍 + 嘴部
 * <p>
 * 模型空间约定（同船模）：基线 y=24（实体原点），XZ 原点居中。身体长轴沿 Z 轴（前后），
 * 背鳍向上（-Y），游动动画摆动尾鳍和尾部身体节段。
 */
public class OceanSharkModel extends EntityModel<OceanSharkEntity> {

    private static final int TEX_W = 128;
    private static final int TEX_H = 128;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart tail;
    private final ModelPart tailFin;
    private final ModelPart dorsalFin;
    private final ModelPart leftPectoralFin;
    private final ModelPart rightPectoralFin;
    private final ModelPart head;
    private final ModelPart jaw;

    public OceanSharkModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.tail = body.getChild("tail");
        this.tailFin = tail.getChild("tail_fin");
        this.dorsalFin = body.getChild("dorsal_fin");
        this.leftPectoralFin = body.getChild("left_pectoral");
        this.rightPectoralFin = body.getChild("right_pectoral");
        this.head = body.getChild("head");
        this.jaw = head.getChild("jaw");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();

        // 躯干主体（鱼雷形）
        PartDefinition bodyDef = part.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-5.0F, -6.0F, -8.0F, 10.0F, 12.0F, 24.0F),
                PartPose.offset(0.0F, 24.0F, -2.0F));

        // 头部（向前收窄）
        bodyDef.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(44, 0)
                        .addBox(-4.0F, -5.0F, -14.0F, 8.0F, 10.0F, 8.0F),
                PartPose.offset(0.0F, -1.0F, -6.0F));

        // 下颌（稍向下张开）
        bodyDef.getChild("head").addOrReplaceChild("jaw",
                CubeListBuilder.create()
                        .texOffs(68, 0)
                        .addBox(-3.0F, -1.0F, -12.0F, 6.0F, 3.0F, 6.0F),
                PartPose.offset(0.0F, 4.0F, -1.0F));

        // 背鳍（三角）
        bodyDef.addOrReplaceChild("dorsal_fin",
                CubeListBuilder.create()
                        .texOffs(0, 38)
                        .addBox(-1.0F, -10.0F, -3.0F, 2.0F, 10.0F, 6.0F),
                PartPose.offset(0.0F, -6.0F, 2.0F));

        // 尾柄（变窄的连接段）
        PartDefinition tailDef = bodyDef.addOrReplaceChild("tail",
                CubeListBuilder.create()
                        .texOffs(0, 56)
                        .addBox(-2.5F, -4.0F, 0.0F, 5.0F, 8.0F, 6.0F),
                PartPose.offset(0.0F, 0.0F, 14.0F));

        // 尾鳍（新月形，左右两叶 + 上叶）
        PartDefinition finDef = tailDef.addOrReplaceChild("tail_fin",
                CubeListBuilder.create()
                        .texOffs(24, 56),
                PartPose.offset(0.0F, 0.0F, 5.0F));
        finDef.addOrReplaceChild("upper_lobe",
                CubeListBuilder.create()
                        .texOffs(24, 56)
                        .addBox(-1.0F, -10.0F, 0.0F, 2.0F, 10.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        finDef.addOrReplaceChild("lower_lobe",
                CubeListBuilder.create()
                        .texOffs(30, 56)
                        .addBox(-1.0F, 0.0F, -2.0F, 2.0F, 8.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        finDef.addOrReplaceChild("rear_tip",
                CubeListBuilder.create()
                        .texOffs(36, 56)
                        .addBox(-1.0F, -2.0F, 1.0F, 2.0F, 4.0F, 6.0F),
                PartPose.offset(0.0F, 1.0F, 0.0F));

        // 左胸鳍
        bodyDef.addOrReplaceChild("left_pectoral",
                CubeListBuilder.create()
                        .texOffs(16, 38)
                        .addBox(-1.0F, 0.0F, -1.5F, 8.0F, 1.0F, 3.0F),
                PartPose.offsetAndRotation(5.0F, 2.0F, -2.0F, 0.0F, 0.0F, 0.5F));

        // 右胸鳍
        bodyDef.addOrReplaceChild("right_pectoral",
                CubeListBuilder.create()
                        .texOffs(16, 42)
                        .addBox(-7.0F, 0.0F, -1.5F, 8.0F, 1.0F, 3.0F),
                PartPose.offsetAndRotation(-5.0F, 2.0F, -2.0F, 0.0F, 0.0F, -0.5F));

        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }

    @Override
    public void setupAnim(OceanSharkEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        // 游泳摆动：尾鳍和尾部左右摆动
        float swimCycle = Mth.sin(ageInTicks * 0.3F) * 0.5F;
        tail.yRot = swimCycle * 0.4F;
        tailFin.yRot = swimCycle * 0.6F;

        // 胸鳍上下扇动
        float finFlap = Mth.sin(ageInTicks * 0.4F) * 0.2F;
        leftPectoralFin.zRot = 0.5F + finFlap;
        rightPectoralFin.zRot = -0.5F - finFlap;

        // 张嘴动画（受伤时）
        float hurt = entity.hurtTime > 0 ? Mth.sin(entity.hurtTime * 0.5F) * 0.3F : 0.0F;
        jaw.xRot = -0.15F + hurt;

        // 头部朝向
        head.yRot = netHeadYaw * ((float) Math.PI / 180.0F) * 0.3F;
        head.xRot = headPitch * ((float) Math.PI / 180.0F) * 0.3F;
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer buffer, int packedLight,
            int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}

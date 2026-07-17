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
 * 鲨鱼盒子模型（128×128 贴图）。5 个鲨鱼变体共用本模型，只换贴图。
 * <p>
 * 模型空间约定（同船/海怪模型）：基线 y=24（实体原点），XZ 居中；身体长轴沿 <b>Z</b>（头在 -Z、尾在 +Z）。
 * 每个盒子的 {@code texOffs} 与尺寸都是<b>刻意规整</b>的圆整数，UV 展开矩形与
 * {@code tools/gen_sixtyseconds_ocean_textures.py} 里逐面上色的矩形一一对应——改模型必须同步改生成脚本。
 *
 * <h3>UV 布局（128×128）</h3>
 * <pre>
 *   body      texOffs(0,0)    8×8×22
 *   head      texOffs(0,32)   6×6×8
 *   snout     texOffs(30,32)  4×3×4
 *   jaw       texOffs(48,32)  6×2×7
 *   dorsal    texOffs(0,50)   1×8×5
 *   tail      texOffs(16,50)  4×6×7
 *   caudalU   texOffs(42,50)  1×9×2
 *   caudalL   texOffs(50,50)  1×6×2
 *   pectoralL texOffs(60,50)  7×1×3
 *   pectoralR texOffs(60,58)  7×1×3
 * </pre>
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

        // 躯干（鱼雷形，头朝 -Z）
        PartDefinition bodyDef = part.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -4.0F, -11.0F, 8.0F, 8.0F, 22.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        // 头（略收窄，接在躯干前端）
        PartDefinition headDef = bodyDef.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 32)
                        .addBox(-3.0F, -3.0F, -8.0F, 6.0F, 6.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, -11.0F));

        // 吻部（尖鼻）
        headDef.addOrReplaceChild("snout",
                CubeListBuilder.create().texOffs(30, 32)
                        .addBox(-2.0F, -2.0F, -12.0F, 4.0F, 3.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // 下颌（微张）
        headDef.addOrReplaceChild("jaw",
                CubeListBuilder.create().texOffs(48, 32)
                        .addBox(-3.0F, 0.0F, -8.0F, 6.0F, 2.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, -0.12F, 0.0F, 0.0F));

        // 背鳍（三角，向上）
        bodyDef.addOrReplaceChild("dorsal_fin",
                CubeListBuilder.create().texOffs(0, 50)
                        .addBox(-0.5F, -8.0F, -2.0F, 1.0F, 8.0F, 5.0F),
                PartPose.offset(0.0F, -4.0F, -2.0F));

        // 尾柄（收窄连接段，接在躯干尾端）
        PartDefinition tailDef = bodyDef.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(16, 50)
                        .addBox(-2.0F, -3.0F, 0.0F, 4.0F, 6.0F, 7.0F),
                PartPose.offset(0.0F, 0.0F, 11.0F));

        // 尾鳍（新月形：上叶长、下叶短）
        PartDefinition finDef = tailDef.addOrReplaceChild("tail_fin",
                CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 7.0F));
        finDef.addOrReplaceChild("caudal_upper",
                CubeListBuilder.create().texOffs(42, 50)
                        .addBox(-0.5F, -9.0F, 0.0F, 1.0F, 9.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.5F, 0.0F, 0.0F));
        finDef.addOrReplaceChild("caudal_lower",
                CubeListBuilder.create().texOffs(50, 50)
                        .addBox(-0.5F, 0.0F, 0.0F, 1.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.4F, 0.0F, 0.0F));

        // 胸鳍 ×2（平展、下压）
        bodyDef.addOrReplaceChild("left_pectoral",
                CubeListBuilder.create().texOffs(60, 50)
                        .addBox(0.0F, -0.5F, -1.5F, 7.0F, 1.0F, 3.0F),
                PartPose.offsetAndRotation(4.0F, 2.0F, -4.0F, 0.0F, 0.0F, 0.45F));
        bodyDef.addOrReplaceChild("right_pectoral",
                CubeListBuilder.create().texOffs(60, 58)
                        .addBox(-7.0F, -0.5F, -1.5F, 7.0F, 1.0F, 3.0F),
                PartPose.offsetAndRotation(-4.0F, 2.0F, -4.0F, 0.0F, 0.0F, -0.45F));

        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }

    @Override
    public void setupAnim(OceanSharkEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        // 游泳摆动：尾柄与尾鳍左右摆
        float swimCycle = Mth.sin(ageInTicks * 0.3F) * 0.5F;
        tail.yRot = swimCycle * 0.4F;
        tailFin.yRot = swimCycle * 0.6F;

        // 胸鳍上下扇动（叠加在放置的 zRot 之上）
        float finFlap = Mth.sin(ageInTicks * 0.4F) * 0.2F;
        leftPectoralFin.zRot = 0.45F + finFlap;
        rightPectoralFin.zRot = -0.45F - finFlap;

        // 张嘴（受伤时更明显）
        float hurt = entity.hurtTime > 0 ? Mth.sin(entity.hurtTime * 0.5F) * 0.3F : 0.0F;
        jaw.xRot = 0.12F + hurt;

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

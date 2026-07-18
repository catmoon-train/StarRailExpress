package net.exmo.sre.sixtyseconds.client.render;

import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 房车盒子模型（贴图 128×128，见 {@code textures/entity/sixty_seconds_rv.png}）。
 *
 * <p>整体足迹 15×50 模型单位（长度方向已加倍），配合渲染器 {@code MODEL_SCALE=3.2F} 渲染为 3×10 格。
 * 坐标约定：X=宽度(±7.5)，Y=离地高度(向上为正，addBox 用 -topHeight)，Z=长度(-25.0 前 .. +25.0 后)。
 *
 * <p>贴图按行分区为纯色带（每个 cube 的 UV 落在对应色带内，各面取均匀颜色）：
 * <ul>
 *   <li>(0,0)   CREAM  车体 — 128×32</li>
 *   <li>(0,32)  WHITE  车顶 — 128×24</li>
 *   <li>(0,56)  DARK   底盘/保险杠/格栅 — 128×32</li>
 *   <li>(0,88)  GLASS  玻璃 — 128×12</li>
 *   <li>(0,100) BLACK  轮胎 — 128×16</li>
 *   <li>(0,116) RED    尾灯 — 128×6</li>
 *   <li>(0,122) YELLOW 前灯 — 128×6</li>
 * </ul>
 *
 * <p><b>驾驶位置空心</b>：前部驾驶舱只由前风挡、侧窗、A 柱、车顶与底盘围合，
 * 内部不填充任何方块，形成可见的空心驾驶舱。
 */
public class SixtySecondsRvModel extends EntityModel<SixtySecondsRvEntity> {

    private final ModelPart root;

    public SixtySecondsRvModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createRv() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();
        // 所有车体部件统一以 y=24（地面）为原点；box 用负 Y 抬升离地。
        // 注：长度方向（Z）整体 ×2，整体足迹 15×50 模型单位，配合 MODEL_SCALE=3.2F 渲染为 3×10 格。
        PartPose ground = PartPose.offset(0.0F, 24.0F, 0.0F);

        // ── CREAM 车体 ───────────────────────────────────────────────
        part.addOrReplaceChild("body_cream", CubeListBuilder.create().texOffs(0, 0)
                        // 驾驶舱前发动机舱（前部下方）：宽15 高5 深8
                        .addBox(-7.5F, -7.0F, -25.0F, 15.0F, 5.0F, 8.0F)
                        // 驾驶舱左/右侧墙（下方门段）：厚0.5 高5 长10
                        .addBox(-7.5F, -7.0F, -17.0F, 0.5F, 5.0F, 10.0F)
                        .addBox(7.0F, -7.0F, -17.0F, 0.5F, 5.0F, 10.0F)
                        // 生活舱主车体：宽15 高14 长32
                        .addBox(-7.5F, -16.0F, -7.0F, 15.0F, 14.0F, 32.0F)
                        // 驾驶舱上方 overcab 突出（床舱）：宽13 高3 深16
                        .addBox(-6.5F, -14.0F, -23.0F, 13.0F, 3.0F, 16.0F)
                        // 右侧乘客门（略凸出）：厚0.3 高8.5 宽5
                        .addBox(7.4F, -11.5F, 6.0F, 0.3F, 8.5F, 5.0F)
                        // A 柱（风挡两侧前角）：厚0.5 高3 深0.8
                        .addBox(-7.5F, -10.0F, -25.0F, 0.5F, 3.0F, 0.8F)
                        .addBox(7.0F, -10.0F, -25.0F, 0.5F, 3.0F, 0.8F)
                        // 车尾爬梯左右立杆：粗0.3 高10 深0.6
                        .addBox(-6.5F, -13.0F, 25.2F, 0.3F, 10.0F, 0.6F)
                        .addBox(6.2F, -13.0F, 25.2F, 0.3F, 10.0F, 0.6F),
                ground);

        // ── DARK 底盘/保险杠/格栅/踏板 ───────────────────────────────
        part.addOrReplaceChild("trim_dark", CubeListBuilder.create().texOffs(0, 56)
                        // 底盘（全足迹，贴地）：宽15 高2 长50
                        .addBox(-7.5F, -2.0F, -25.0F, 15.0F, 2.0F, 50.0F)
                        // 前后保险杠（各凸出2）：宽15 高2.5 深2
                        .addBox(-7.5F, -2.5F, -27.0F, 15.0F, 2.5F, 2.0F)
                        .addBox(-7.5F, -2.5F, 25.0F, 15.0F, 2.5F, 2.0F)
                        // 前格栅（略凸前脸）：宽10 高2.5 深0.6
                        .addBox(-5.0F, -4.5F, -25.2F, 10.0F, 2.5F, 0.6F)
                        // 侧踏板（前后轮之间）：厚0.7 高0.5 长14
                        .addBox(-8.2F, -1.0F, -6.0F, 0.7F, 0.5F, 14.0F)
                        .addBox(7.5F, -1.0F, -6.0F, 0.7F, 0.5F, 14.0F),
                ground);

        // ── WHITE 车顶 ───────────────────────────────────────────────
        part.addOrReplaceChild("roof_white", CubeListBuilder.create().texOffs(0, 32)
                        // 生活舱顶：宽15 高2 长32
                        .addBox(-7.5F, -18.0F, -7.0F, 15.0F, 2.0F, 32.0F)
                        // 驾驶舱顶：宽15 高1 长18
                        .addBox(-7.5F, -11.0F, -25.0F, 15.0F, 1.0F, 18.0F)
                        // overcab 顶：宽13 高1 长16
                        .addBox(-6.5F, -15.0F, -23.0F, 13.0F, 1.0F, 16.0F)
                        // 车顶空调外机：宽4 高1 深6
                        .addBox(-2.0F, -19.0F, 10.0F, 4.0F, 1.0F, 6.0F)
                        // 车顶通风帽：宽1.5 高0.7 深3
                        .addBox(3.0F, -18.7F, -2.0F, 1.5F, 0.7F, 3.0F),
                ground);

        // ── GLASS 玻璃 ───────────────────────────────────────────────
        part.addOrReplaceChild("glass", CubeListBuilder.create().texOffs(0, 88)
                        // 前风挡（A 柱之间）：宽14 高3 深0.8
                        .addBox(-7.0F, -10.0F, -25.1F, 14.0F, 3.0F, 0.8F)
                        // 驾驶舱侧窗左/右：厚0.3 高2.5 长15
                        .addBox(-7.5F, -10.0F, -23.0F, 0.3F, 2.5F, 15.0F)
                        .addBox(7.2F, -10.0F, -23.0F, 0.3F, 2.5F, 15.0F)
                        // 生活舱侧窗（前/后各一）左
                        .addBox(-7.5F, -11.0F, -4.0F, 0.3F, 3.0F, 8.0F)
                        .addBox(-7.5F, -11.0F, 8.0F, 0.3F, 3.0F, 10.0F)
                        // 生活舱侧窗（前/后各一）右
                        .addBox(7.2F, -11.0F, -4.0F, 0.3F, 3.0F, 8.0F)
                        .addBox(7.2F, -11.0F, 8.0F, 0.3F, 3.0F, 10.0F)
                        // 车尾窗：宽10 高3 深0.6
                        .addBox(-5.0F, -13.0F, 25.0F, 10.0F, 3.0F, 0.6F)
                        // overcab 前观景窗：宽8 高2 深0.6
                        .addBox(-4.0F, -13.0F, -23.0F, 8.0F, 2.0F, 0.6F)
                        // 车头前窗（发动机舱上方前脸开口）：宽10 高3 深0.6
                        .addBox(-5.0F, -6.0F, -25.2F, 10.0F, 3.0F, 0.6F),
                ground);

        // ── YELLOW 前大灯 ────────────────────────────────────────────
        part.addOrReplaceChild("lights_yellow", CubeListBuilder.create().texOffs(0, 122)
                        .addBox(-6.0F, -5.5F, -25.2F, 2.0F, 1.5F, 0.6F)
                        .addBox(4.0F, -5.5F, -25.2F, 2.0F, 1.5F, 0.6F),
                ground);

        // ── RED 尾灯 ────────────────────────────────────────────────
        part.addOrReplaceChild("lights_red", CubeListBuilder.create().texOffs(0, 116)
                        .addBox(-6.0F, -5.5F, 25.0F, 2.0F, 1.5F, 0.6F)
                        .addBox(4.0F, -5.5F, 25.0F, 2.0F, 1.5F, 0.6F),
                ground);

        // ── BLACK 轮胎（前轴 + 后双联轴，共 6 轮；位置 z ×2，车轮本身大小不变）────────────────────
        // 注：cube 中心对齐 ModelPart 原点（addBox y=-2.5，PartPose y=21.5），
        // 这样 setupAnim 的 xRot（滚动）/yRot（转向）都绕车轮中心生效。
        CubeListBuilder wheel = CubeListBuilder.create()
                .texOffs(0, 100).addBox(-1.25F, -2.5F, -2.5F, 2.5F, 5.0F, 5.0F);
        part.addOrReplaceChild("wheel_fl", wheel, PartPose.offset(-7.5F, 21.5F, -16.0F));
        part.addOrReplaceChild("wheel_fr", wheel, PartPose.offset(7.5F, 21.5F, -16.0F));
        part.addOrReplaceChild("wheel_r1l", wheel, PartPose.offset(-7.5F, 21.5F, 10.0F));
        part.addOrReplaceChild("wheel_r1r", wheel, PartPose.offset(7.5F, 21.5F, 10.0F));
        part.addOrReplaceChild("wheel_r2l", wheel, PartPose.offset(-7.5F, 21.5F, 17.0F));
        part.addOrReplaceChild("wheel_r2r", wheel, PartPose.offset(7.5F, 21.5F, 17.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(SixtySecondsRvEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        // 6 个车轮滚动：绕 X 轴旋转，角度来自实体客户端累积的 wheelRotation（throttle 驱动）
        float spin = entity.wheelRotation;
        ModelPart wheelFl = root.getChild("wheel_fl");
        ModelPart wheelFr = root.getChild("wheel_fr");
        ModelPart wheelR1l = root.getChild("wheel_r1l");
        ModelPart wheelR1r = root.getChild("wheel_r1r");
        ModelPart wheelR2l = root.getChild("wheel_r2l");
        ModelPart wheelR2r = root.getChild("wheel_r2r");
        wheelFl.xRot = spin;
        wheelFr.xRot = spin;
        wheelR1l.xRot = spin;
        wheelR1r.xRot = spin;
        wheelR2l.xRot = spin;
        wheelR2r.xRot = spin;

        // 前轮转向：根据 steering 偏转，最大 0.5 rad（约 28°）
        float steer = entity.steering() * 0.5F;
        wheelFl.yRot = steer;
        wheelFr.yRot = steer;
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}

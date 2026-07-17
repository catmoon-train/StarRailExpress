package net.exmo.sre.sixtyseconds.client.render;

import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsSeaVehicleEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 60s 海上载具的自研盒子模型（贴图 128×128，见 {@code textures/entity/sixty_seconds_raft/motorboat/fishing_boat.png}）。
 * <p>
 * 与陆上载具模型（{@link SixtySecondsVehicleModel}）同一套写法：模型基线 y=24（船底压在实体原点上）、
 * 无动画、朝向由渲染器按 yaw 处理、在渲染器里直接 {@code bakeRoot()} 不占公共 ModelLayer 注册表。
 * <p>
 * 三种外形刻意拉开剪影，不靠颜色区分：木筏=纯平板木排（无舷、有撑杆）、
 * 汽艇=尖艏低舷+挡风玻璃+舷外机、渔船=高舷+驾驶室+吊杆。
 */
public class SixtySecondsSeaVehicleModel extends EntityModel<SixtySecondsSeaVehicleEntity> {

    /** 贴图尺寸：船身盒子的 UV 展开很宽（渔船船身单个就 96px），64 装不下。 */
    private static final int TEX_W = 128;
    private static final int TEX_H = 128;

    private final ModelPart root;

    public SixtySecondsSeaVehicleModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createFor(SixtySecondsSeaVehicleEntity.Kind kind) {
        return switch (kind) {
            case RAFT -> createRaft();
            case MOTORBOAT -> createMotorboat();
            case FISHING_BOAT -> createFishingBoat();
        };
    }

    /** 木筏：一块 20×20 的原木平板 + 底下两条横梁 + 一根撑杆。没有舷——踩上去就是块板。 */
    public static LayerDefinition createRaft() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();
        // 甲板（原木排面）
        part.addOrReplaceChild("deck", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10.0F, -2.0F, -10.0F, 20.0F, 2.0F, 20.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 底部横梁 ×2（浮筒/捆扎木）
        CubeListBuilder beam = CubeListBuilder.create()
                .texOffs(0, 26).addBox(-10.0F, -4.0F, -1.5F, 20.0F, 2.0F, 3.0F);
        part.addOrReplaceChild("beam_front", beam, PartPose.offset(0.0F, 24.0F, -6.0F));
        part.addOrReplaceChild("beam_rear", beam, PartPose.offset(0.0F, 24.0F, 6.0F));
        // 撑杆（斜插在角上）
        part.addOrReplaceChild("pole", CubeListBuilder.create()
                .texOffs(56, 26).addBox(-0.5F, -16.0F, -0.5F, 1.0F, 16.0F, 1.0F),
                PartPose.offsetAndRotation(8.0F, 24.0F, 8.0F, 0.18F, 0.0F, -0.18F));
        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }

    /** 汽艇：低舷船身 + 尖艏 + 挡风玻璃 + 舷外机。 */
    public static LayerDefinition createMotorboat() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();
        // 船身
        part.addOrReplaceChild("hull", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0F, -4.0F, -12.0F, 12.0F, 4.0F, 24.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 尖艏（前方收窄的一截）
        part.addOrReplaceChild("bow", CubeListBuilder.create()
                .texOffs(0, 30).addBox(-4.0F, -3.5F, -16.0F, 8.0F, 3.0F, 4.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 挡风玻璃
        part.addOrReplaceChild("windshield", CubeListBuilder.create()
                .texOffs(26, 30).addBox(-5.0F, -8.0F, -5.0F, 10.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.25F, 0.0F, 0.0F));
        // 舷外机（船尾）
        part.addOrReplaceChild("motor", CubeListBuilder.create()
                .texOffs(50, 30).addBox(-1.5F, -7.0F, 12.0F, 3.0F, 5.0F, 4.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 座椅 ×2
        CubeListBuilder seat = CubeListBuilder.create()
                .texOffs(0, 40).addBox(-3.0F, -5.0F, -2.5F, 6.0F, 1.0F, 5.0F);
        part.addOrReplaceChild("seat_front", seat, PartPose.offset(0.0F, 24.0F, -3.0F));
        part.addOrReplaceChild("seat_rear", seat, PartPose.offset(0.0F, 24.0F, 6.0F));
        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }

    /** 渔船：大船身 + 高舷 + 驾驶室 + 吊杆。 */
    public static LayerDefinition createFishingBoat() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();
        // 船身
        part.addOrReplaceChild("hull", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8.0F, -5.0F, -16.0F, 16.0F, 5.0F, 32.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 舷墙（左右两片矮墙）
        CubeListBuilder rail = CubeListBuilder.create()
                .texOffs(0, 40).addBox(-0.5F, -8.0F, -16.0F, 1.0F, 3.0F, 32.0F);
        part.addOrReplaceChild("rail_left", rail, PartPose.offset(-7.5F, 24.0F, 0.0F));
        part.addOrReplaceChild("rail_right", rail, PartPose.offset(7.5F, 24.0F, 0.0F));
        // 驾驶室（偏船尾）
        part.addOrReplaceChild("cabin", CubeListBuilder.create()
                .texOffs(70, 40).addBox(-6.0F, -14.0F, -5.0F, 12.0F, 9.0F, 10.0F),
                PartPose.offset(0.0F, 24.0F, 8.0F));
        // 吊杆（桅 + 横臂）
        part.addOrReplaceChild("mast", CubeListBuilder.create()
                .texOffs(0, 78).addBox(-1.0F, -24.0F, -1.0F, 2.0F, 10.0F, 2.0F),
                PartPose.offset(0.0F, 24.0F, -2.0F));
        part.addOrReplaceChild("boom", CubeListBuilder.create()
                .texOffs(16, 78).addBox(-1.0F, -24.0F, -10.0F, 2.0F, 2.0F, 10.0F),
                PartPose.offsetAndRotation(0.0F, 24.0F, -2.0F, 0.30F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }

    @Override
    public void setupAnim(SixtySecondsSeaVehicleEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        // 静态模型，无动画（水面起伏由原版 Boat 的浮力自己带出来）
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}

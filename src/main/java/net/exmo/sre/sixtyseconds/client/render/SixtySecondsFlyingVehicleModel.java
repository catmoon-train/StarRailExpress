package net.exmo.sre.sixtyseconds.client.render;

import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsFlyingVehicleEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 60s 飞行载具盒子模型（贴图 64×64，见 {@code textures/entity/sixty_seconds_flyer/helicopter/airplane.png}）。
 * 无动画，朝向由渲染器按 yaw 处理。
 */
public class SixtySecondsFlyingVehicleModel extends EntityModel<SixtySecondsFlyingVehicleEntity> {

    private static final int TEX_W = 64;
    private static final int TEX_H = 64;

    private final ModelPart root;

    public SixtySecondsFlyingVehicleModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createFor(SixtySecondsFlyingVehicleEntity.Kind kind) {
        return switch (kind) {
            case FLYER -> createFlyer();
            case HELICOPTER -> createHelicopter();
            case AIRPLANE -> createAirplane();
        };
    }

    /** 飞行器：小型单人飞行平台，长方形主体 + 四个角落的旋翼支臂 */
    public static LayerDefinition createFlyer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();
        // 主体（座位平台）
        part.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 2.0F, 8.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 驾驶杆
        part.addOrReplaceChild("stick", CubeListBuilder.create()
                .texOffs(28, 0).addBox(-0.5F, -6.0F, -2.5F, 1.0F, 5.0F, 1.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 四个旋翼支臂
        CubeListBuilder arm = CubeListBuilder.create()
                .texOffs(0, 10).addBox(-0.5F, -1.0F, -8.0F, 1.0F, 1.0F, 8.0F);
        part.addOrReplaceChild("arm_fl", arm, PartPose.offsetAndRotation(3.0F, 24.0F, -4.0F, 0.0F, 0.785F, 0.0F));
        part.addOrReplaceChild("arm_fr", arm, PartPose.offsetAndRotation(-3.0F, 24.0F, -4.0F, 0.0F, -0.785F, 0.0F));
        part.addOrReplaceChild("arm_rl", arm, PartPose.offsetAndRotation(3.0F, 24.0F, 4.0F, 0.0F, -0.785F, 0.0F));
        part.addOrReplaceChild("arm_rr", arm, PartPose.offsetAndRotation(-3.0F, 24.0F, 4.0F, 0.0F, 0.785F, 0.0F));
        // 旋翼桨叶（薄板）
        CubeListBuilder rotor = CubeListBuilder.create()
                .texOffs(32, 12).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 6.0F);
        part.addOrReplaceChild("rotor_fl", rotor, PartPose.offset(7.0F, 23.0F, -9.0F));
        part.addOrReplaceChild("rotor_fr", rotor, PartPose.offset(-7.0F, 23.0F, -9.0F));
        part.addOrReplaceChild("rotor_rl", rotor, PartPose.offset(7.0F, 23.0F, 9.0F));
        part.addOrReplaceChild("rotor_rr", rotor, PartPose.offset(-7.0F, 23.0F, 9.0F));
        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }

    /** 直升机：机身 + 尾梁 + 主旋翼 + 尾旋翼 + 起落架 */
    public static LayerDefinition createHelicopter() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();
        // 机身（驾驶舱）
        part.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -6.0F, -12.0F, 8.0F, 6.0F, 18.0F),
                PartPose.offset(0.0F, 24.0F, -2.0F));
        // 驾驶舱玻璃
        part.addOrReplaceChild("cockpit", CubeListBuilder.create()
                .texOffs(0, 24).addBox(-3.0F, -8.0F, -8.0F, 6.0F, 3.0F, 6.0F),
                PartPose.offsetAndRotation(0.0F, 24.0F, -2.0F, -0.35F, 0.0F, 0.0F));
        // 尾梁
        part.addOrReplaceChild("tail_boom", CubeListBuilder.create()
                .texOffs(34, 0).addBox(-1.0F, -3.0F, 6.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offset(0.0F, 24.0F, -2.0F));
        // 尾翼（垂直）
        part.addOrReplaceChild("tail_fin", CubeListBuilder.create()
                .texOffs(34, 16).addBox(-0.5F, -7.0F, 19.0F, 1.0F, 5.0F, 3.0F),
                PartPose.offset(0.0F, 24.0F, -2.0F));
        // 尾水平翼
        part.addOrReplaceChild("tail_hstab", CubeListBuilder.create()
                .texOffs(42, 16).addBox(-4.0F, -3.5F, 18.0F, 8.0F, 1.0F, 3.0F),
                PartPose.offset(0.0F, 24.0F, -2.0F));
        // 尾旋翼
        part.addOrReplaceChild("tail_rotor", CubeListBuilder.create()
                .texOffs(52, 0).addBox(-3.0F, -0.5F, 19.5F, 6.0F, 1.0F, 1.0F),
                PartPose.offset(0.0F, 24.0F, -2.0F));
        // 主旋翼轴
        part.addOrReplaceChild("rotor_mast", CubeListBuilder.create()
                .texOffs(52, 6).addBox(-0.5F, -10.0F, -0.5F, 1.0F, 4.0F, 1.0F),
                PartPose.offset(0.0F, 24.0F, 1.0F));
        // 主旋翼（长条形）
        part.addOrReplaceChild("main_rotor", CubeListBuilder.create()
                .texOffs(34, 24).addBox(-14.0F, -0.5F, -0.5F, 28.0F, 1.0F, 1.0F),
                PartPose.offset(0.0F, 14.0F, 1.0F));
        // 起落架滑橇
        CubeListBuilder skid = CubeListBuilder.create()
                .texOffs(0, 34).addBox(-0.5F, -2.0F, -8.0F, 1.0F, 2.0F, 16.0F);
        part.addOrReplaceChild("skid_left", skid, PartPose.offset(3.5F, 24.0F, -2.0F));
        part.addOrReplaceChild("skid_right", skid, PartPose.offset(-3.5F, 24.0F, -2.0F));
        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }

    /** 飞机：机身 + 主翼 + 尾翼 + 引擎 + 起落架 */
    public static LayerDefinition createAirplane() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();
        // 机身（长圆柱/盒状）
        part.addOrReplaceChild("fuselage", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -4.0F, -18.0F, 8.0F, 8.0F, 36.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 机头
        part.addOrReplaceChild("nose", CubeListBuilder.create()
                .texOffs(0, 10).addBox(-3.0F, -3.0F, -21.0F, 6.0F, 6.0F, 3.0F),
                PartPose.offset(0.0F, 25.0F, 0.0F));
        // 驾驶舱
        part.addOrReplaceChild("cockpit", CubeListBuilder.create()
                .texOffs(0, 20).addBox(-3.0F, -8.0F, -15.0F, 6.0F, 4.0F, 8.0F),
                PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.2F, 0.0F, 0.0F));
        // 主翼
        part.addOrReplaceChild("main_wing", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-30.0F, -2.0F, -6.0F, 60.0F, 2.0F, 8.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 水平尾翼
        part.addOrReplaceChild("tail_wing", CubeListBuilder.create()
                .texOffs(0, 42).addBox(-10.0F, -1.5F, 14.0F, 20.0F, 1.0F, 4.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 垂直尾翼
        part.addOrReplaceChild("tail_fin", CubeListBuilder.create()
                .texOffs(52, 0).addBox(-0.5F, -8.0F, 14.0F, 1.0F, 6.0F, 5.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 引擎 ×2（翼下）
        CubeListBuilder engine = CubeListBuilder.create()
                .texOffs(52, 12).addBox(-2.0F, -2.0F, -4.0F, 4.0F, 4.0F, 6.0F);
        part.addOrReplaceChild("engine_left", engine, PartPose.offset(6.0F, 26.0F, -6.0F));
        part.addOrReplaceChild("engine_right", engine, PartPose.offset(-6.0F, 26.0F, -6.0F));
        // 起落架
        CubeListBuilder gear = CubeListBuilder.create()
                .texOffs(48, 24).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F);
        part.addOrReplaceChild("gear_front", gear, PartPose.offset(0.0F, 24.0F, -12.0F));
        part.addOrReplaceChild("gear_left", gear, PartPose.offset(6.0F, 24.0F, -4.0F));
        part.addOrReplaceChild("gear_right", gear, PartPose.offset(-6.0F, 24.0F, -4.0F));
        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }

    @Override
    public void setupAnim(SixtySecondsFlyingVehicleEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        // 静态模型
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}

package net.exmo.sre.sixtyseconds.client.render;

import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsVehicleEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 60s 载具的简易盒子模型（贴图 64×64，见 {@code textures/entity/sixty_seconds_motorcycle/car.png}）。
 * 无动画（车轮不转），车体朝向由 LivingEntityRenderer 按 yBodyRot 处理。
 * 模型在渲染器里直接 {@code bakeRoot()}（不注册 ModelLayer，避免占公共注册表）。
 */
public class SixtySecondsVehicleModel extends EntityModel<SixtySecondsVehicleEntity> {

    private final ModelPart root;

    public SixtySecondsVehicleModel(ModelPart root) {
        this.root = root;
    }

    /** 摩托车：车架 + 前后轮 + 车把 + 坐垫。 */
    public static LayerDefinition createMotorcycle() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();
        // 车架（低矮长条）
        part.addOrReplaceChild("frame", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.0F, -12.0F, -8.0F, 4.0F, 4.0F, 16.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 坐垫
        part.addOrReplaceChild("seat", CubeListBuilder.create()
                .texOffs(0, 20).addBox(-2.5F, -14.0F, -1.0F, 5.0F, 2.0F, 7.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 前轮 / 后轮（薄盒子）
        part.addOrReplaceChild("front_wheel", CubeListBuilder.create()
                .texOffs(0, 29).addBox(-1.0F, -6.0F, -3.0F, 2.0F, 6.0F, 6.0F),
                PartPose.offset(0.0F, 24.0F, -8.0F));
        part.addOrReplaceChild("rear_wheel", CubeListBuilder.create()
                .texOffs(16, 29).addBox(-1.0F, -6.0F, -3.0F, 2.0F, 6.0F, 6.0F),
                PartPose.offset(0.0F, 24.0F, 8.0F));
        // 车把
        part.addOrReplaceChild("handlebar", CubeListBuilder.create()
                .texOffs(24, 20).addBox(-4.0F, -17.0F, -0.5F, 8.0F, 1.0F, 1.0F)
                .texOffs(24, 22).addBox(-0.5F, -16.0F, -0.5F, 1.0F, 4.0F, 1.0F),
                PartPose.offset(0.0F, 24.0F, -7.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** 小汽车：车身 + 驾驶舱 + 四轮。 */
    public static LayerDefinition createCar() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();
        // 车身（底盘）
        part.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0F, -8.0F, -10.0F, 12.0F, 5.0F, 20.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 驾驶舱（车顶）
        part.addOrReplaceChild("cabin", CubeListBuilder.create()
                .texOffs(0, 25).addBox(-5.0F, -14.0F, -4.0F, 10.0F, 6.0F, 12.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        // 四轮
        CubeListBuilder wheel = CubeListBuilder.create()
                .texOffs(44, 25).addBox(-1.0F, -4.0F, -2.0F, 2.0F, 4.0F, 4.0F);
        part.addOrReplaceChild("wheel_fl", wheel, PartPose.offset(-6.5F, 24.0F, -7.0F));
        part.addOrReplaceChild("wheel_fr", wheel, PartPose.offset(6.5F, 24.0F, -7.0F));
        part.addOrReplaceChild("wheel_rl", wheel, PartPose.offset(-6.5F, 24.0F, 7.0F));
        part.addOrReplaceChild("wheel_rr", wheel, PartPose.offset(6.5F, 24.0F, 7.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(SixtySecondsVehicleEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        // 静态模型，无动画
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}

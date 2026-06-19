package io.wifi.starrailexpress.client.model.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 自定义玩家 plush 的模型：plush（fumo）轮廓——大头、矮身、短手短脚、小耳朵。
 * <p>
 * 关键点：每个立方体的 UV 都映射到<b>标准 64×64 玩家皮肤</b>布局（头/帽、身体、手臂、腿），
 * 因此渲染时直接绑定玩家皮肤贴图即可“把皮肤穿在 plush 形状上”，无需运行时重绘贴图。
 * 坐标采用实体模型像素约定（脚在 y=24，向上为 -y），由方块实体渲染器统一翻转/缩放到方块空间。
 */
public class CustomPlayerPlushModel {
    private final ModelPart root;

    public CustomPlayerPlushModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 头（8×8×8，皮肤头部 UV 0,0），y 8..16；带帽子覆盖层（UV 32,0）
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                        .texOffs(32, 0).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)),
                PartPose.offset(0.0F, 8.0F, 0.0F));

        // 小耳朵（映射到头顶皮肤像素），点缀 plush 感
        head.addOrReplaceChild("ear_left",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offset(-3.0F, 1.0F, 0.0F));
        head.addOrReplaceChild("ear_right",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offset(3.0F, 1.0F, 0.0F));

        // 身体（矮，皮肤身体 UV 16,16），y 16..22
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 6.0F, 4.0F),
                PartPose.offset(0.0F, 16.0F, 0.0F));

        // 手臂（短粗，皮肤手臂 UV），位于身体两侧 y 16..20
        root.addOrReplaceChild("arm_right",
                CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, 0.0F, -2.0F, 3.0F, 4.0F, 4.0F),
                PartPose.offset(-4.0F, 16.0F, 0.0F));
        root.addOrReplaceChild("arm_left",
                CubeListBuilder.create().texOffs(32, 48).addBox(0.0F, 0.0F, -2.0F, 3.0F, 4.0F, 4.0F),
                PartPose.offset(4.0F, 16.0F, 0.0F));

        // 腿（短，皮肤腿部 UV），y 22..24
        root.addOrReplaceChild("leg_right",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 4.0F),
                PartPose.offset(-2.0F, 22.0F, 0.0F));
        root.addOrReplaceChild("leg_left",
                CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 4.0F),
                PartPose.offset(2.0F, 22.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    public void render(PoseStack poseStack, VertexConsumer vertexConsumer, int light, int overlay) {
        this.root.render(poseStack, vertexConsumer, light, overlay);
    }
}

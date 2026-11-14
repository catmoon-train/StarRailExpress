package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** 独立厉鬼网格：布袋、长肢、破布尾与单独的发光眼，不借用玩家模型。 */
public final class SackGhostModel extends EntityModel<Entity> {
    private final ModelPart root;
    private final ModelPart hood;
    private final ModelPart eyes;
    private final ModelPart mouth;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart[] rags = new ModelPart[5];

    public SackGhostModel() {
        root = layer().bakeRoot();
        hood = root.getChild("hood");
        eyes = hood.getChild("eyes");
        mouth = hood.getChild("mouth");
        leftArm = root.getChild("left_arm");
        rightArm = root.getChild("right_arm");
        for (int i = 0; i < rags.length; i++) rags[i] = root.getChild("rag" + i);
    }

    private static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var hood = root.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.3F, -3, -3.5F, 8.6F, 8.4F, 7), PartPose.offset(0, -1, 0));
        // 以收口布袋和短裂片交代材质，不再用大方头及外翻硬片抢占轮廓。
        hood.addOrReplaceChild("hem", CubeListBuilder.create().texOffs(40, 0)
                .addBox(-4.45F, 2.8F, -3.7F, 1.3F, 3.4F, 0.65F)
                .addBox(2.8F, 3.4F, -3.65F, 1.45F, 2.5F, 0.65F), PartPose.ZERO);
        hood.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(60, 60)
                .addBox(-2.8F, -0.3F, -3.62F, 1.55F, 0.4F, 0.08F)
                .addBox(1.2F, -0.6F, -3.62F, 1.55F, 0.4F, 0.08F), PartPose.ZERO);
        hood.addOrReplaceChild("mouth", CubeListBuilder.create().texOffs(56, 56)
                .addBox(-0.85F, 0, -3.65F, 1.7F, 1.3F, 0.08F), PartPose.offset(0, 1.3F, 0));
        root.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-3.1F, 0, -1.85F, 6.2F, 10.8F, 3.7F), PartPose.offset(0, 4.3F, 0));
        root.addOrReplaceChild("left_arm", arm(), PartPose.offset(3.3F, 5, 0));
        root.addOrReplaceChild("right_arm", arm(), PartPose.offset(-3.3F, 5, 0));
        for (int i = 0; i < 5; i++) {
            root.addOrReplaceChild("rag" + i, CubeListBuilder.create().texOffs(34 + i * 4, 32)
                    .addBox(-0.85F, 0, -0.12F, 1.7F, 5.7F + i % 3 * 0.65F, 0.65F)
                    .addBox(-0.5F, 5.3F + i % 3 * 0.65F, -0.1F, 1, 2.1F, 0.55F),
                    PartPose.offset((i - 2) * 1.15F, 14 + i % 2 * 0.6F, 0.3F));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static CubeListBuilder arm() {
        return CubeListBuilder.create().texOffs(18, 32).addBox(-0.8F, 0, -0.8F, 1.6F, 8, 1.6F)
                .texOffs(28, 32).addBox(-1.2F, 7.5F, -0.85F, 2.4F, 3, 1.5F)
                .addBox(-1.2F, 10, -0.85F, 0.5F, 2.5F, 0.7F)
                .addBox(-0.25F, 10, -0.85F, 0.5F, 3, 0.7F)
                .addBox(0.7F, 10, -0.85F, 0.5F, 2.2F, 0.7F);
    }

    public void animate(float time, float yaw, float pitch, float scream, float reach) {
        hood.yRot = yaw * Mth.DEG_TO_RAD;
        hood.xRot = pitch * Mth.DEG_TO_RAD + Mth.sin(time * 0.035F) * 0.015F;
        hood.zRot = Mth.sin(time * 0.045F) * 0.018F;
        mouth.yScale = 0.14F + scream * 0.9F;
        leftArm.xRot = -reach * 1.3F - scream * 0.15F + Mth.sin(time * 0.035F) * 0.035F;
        rightArm.xRot = -reach * 1.3F - scream * 0.15F - Mth.sin(time * 0.04F) * 0.035F;
        leftArm.zRot = -0.08F - scream * 0.25F;
        rightArm.zRot = 0.08F + scream * 0.25F;
        for (int i = 0; i < rags.length; i++) {
            rags[i].xRot = 0.12F + Mth.sin(time * 0.045F + i * 1.9F) * 0.07F;
            rags[i].zRot = Mth.sin(time * 0.035F + i) * 0.04F;
        }
    }

    @Override
    public void setupAnim(Entity entity, float walk, float speed, float age, float yaw, float pitch) {
        animate(age, yaw, pitch, 0, speed);
    }

    @Override
    public void renderToBuffer(PoseStack poses, VertexConsumer out, int light, int overlay, int color) {
        eyes.visible = false;
        root.render(poses, out, light, overlay, color);
        eyes.visible = true;
    }

    public void renderEyes(PoseStack poses, VertexConsumer out, int color) {
        poses.pushPose();
        hood.translateAndRotate(poses);
        eyes.render(poses, out, 0xF000F0, OverlayTexture.NO_OVERLAY, color);
        poses.popPose();
    }
}

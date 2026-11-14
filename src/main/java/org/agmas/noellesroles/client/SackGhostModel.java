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
                .addBox(-5, -11, -4.5F, 10, 11, 9), PartPose.offset(0, -4, 0));
        // 不规则袋口与一侧裂开的布片，眼睛位于袋面前方。
        hood.addOrReplaceChild("hem", CubeListBuilder.create().texOffs(40, 0)
                .addBox(-5.4F, -2, -4.7F, 3, 6, 1)
                .addBox(2.5F, -1, -4.7F, 3, 4, 1), PartPose.ZERO);
        hood.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(60, 60)
                .addBox(-3.3F, -7, -4.85F, 2.1F, 0.75F, 0.1F)
                .addBox(1.2F, -7.5F, -4.85F, 2.1F, 0.75F, 0.1F), PartPose.ZERO);
        hood.addOrReplaceChild("mouth", CubeListBuilder.create().texOffs(56, 56)
                .addBox(-1.6F, 0, -4.9F, 3.2F, 3, 0.1F), PartPose.offset(0, -4.5F, 0));
        root.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-2.5F, -4, -1.5F, 5, 21, 3), PartPose.ZERO);
        root.addOrReplaceChild("left_arm", arm(), PartPose.offset(3, -2, 0));
        root.addOrReplaceChild("right_arm", arm(), PartPose.offset(-3, -2, 0));
        for (int i = 0; i < 5; i++) {
            root.addOrReplaceChild("rag" + i, CubeListBuilder.create().texOffs(34 + i * 4, 32)
                    .addBox(-1, 0, 0, 2, 13 + i % 3 * 3, 1),
                    PartPose.offset((i - 2) * 1.35F, 10 + i % 2, 0.8F));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static CubeListBuilder arm() {
        return CubeListBuilder.create().texOffs(18, 32).addBox(-1, 0, -1, 2, 23, 2)
                .texOffs(28, 32).addBox(-1.8F, 21, -1.3F, 3.6F, 4, 2)
                .addBox(-1.8F, 24, -1.3F, 0.7F, 5, 1)
                .addBox(-0.4F, 24, -1.3F, 0.7F, 6, 1)
                .addBox(1, 24, -1.3F, 0.7F, 4, 1);
    }

    public void animate(float time, float yaw, float pitch, float scream, float reach) {
        hood.yRot = yaw * Mth.DEG_TO_RAD;
        hood.xRot = pitch * Mth.DEG_TO_RAD + Mth.sin(time * 0.08F) * 0.06F;
        hood.zRot = Mth.sin(time * 0.11F) * 0.07F;
        mouth.yScale = 0.18F + scream * 1.5F;
        leftArm.xRot = -reach * 1.7F - scream * 0.45F + Mth.sin(time * 0.08F) * 0.12F;
        rightArm.xRot = -reach * 1.7F - scream * 0.45F - Mth.sin(time * 0.09F) * 0.12F;
        leftArm.zRot = -0.12F - scream * 0.5F;
        rightArm.zRot = 0.12F + scream * 0.5F;
        for (int i = 0; i < rags.length; i++) {
            rags[i].xRot = 0.25F + Mth.sin(time * 0.12F + i * 1.9F) * 0.22F;
            rags[i].zRot = Mth.sin(time * 0.075F + i) * 0.13F;
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

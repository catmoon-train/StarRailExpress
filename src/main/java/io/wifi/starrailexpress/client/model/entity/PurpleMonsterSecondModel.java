/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.model.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.entity.PurpleMonsterSecondEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 紫怪第二形态：细肢悬挂，丝线吊在上方。
 */
public class PurpleMonsterSecondModel extends EntityModel<PurpleMonsterSecondEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SRE.id("purple_monster_second"), "main");

    private final ModelPart root;
    private final ModelPart silk;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;
    private final ModelPart midLeftLimb;
    private final ModelPart midRightLimb;

    public PurpleMonsterSecondModel(ModelPart root) {
        this.root = root.getChild("root");
        this.silk = this.root.getChild("silk");
        this.head = this.root.getChild("head");
        this.body = this.root.getChild("body");
        this.leftArm = this.root.getChild("left_arm");
        this.rightArm = this.root.getChild("right_arm");
        this.leftLeg = this.root.getChild("left_leg");
        this.rightLeg = this.root.getChild("right_leg");
        this.midLeftLimb = this.root.getChild("mid_left_limb");
        this.midRightLimb = this.root.getChild("mid_right_limb");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        root.addOrReplaceChild("silk",
                CubeListBuilder.create()
                        .texOffs(60, 0).addBox(-0.5F, -18.0F, -0.5F, 1.0F, 18.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.5F, -38.0F, 0.0F));

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.5F, -3.5F, -3.5F, 7.0F, 7.0F, 7.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-8.0F, -24.0F, 0.0F, 0.22F, 0.55F, -0.18F));
        head.addOrReplaceChild("eye",
                CubeListBuilder.create()
                        .texOffs(0, 14).addBox(-1.2F, -0.6F, -3.7F, 2.5F, 1.2F, 0.6F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        root.addOrReplaceChild("neck",
                CubeListBuilder.create()
                        .texOffs(28, 0).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-3.5F, -21.0F, 0.0F, 0.25F, 0.35F, -0.55F));

        root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(36, 0).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(36, 14).addBox(-2.5F, 3.0F, -2.5F, 5.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(1.0F, -18.0F, 0.0F, 0.15F, 0.0F, 0.1F));

        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 16.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(8, 16).addBox(-0.5F, 14.0F, -0.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-2.0F, -23.0F, 0.0F, 0.4F, 0.25F, 1.95F));

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(12, 16).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 15.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(20, 16).addBox(-0.5F, 13.0F, -0.5F, 1.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(3.0F, -24.0F, 0.0F, -0.6F, -0.2F, -2.25F));

        root.addOrReplaceChild("mid_left_limb",
                CubeListBuilder.create()
                        .texOffs(24, 16).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 14.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-1.0F, -16.0F, 1.0F, 0.75F, 0.45F, 1.05F));

        root.addOrReplaceChild("mid_right_limb",
                CubeListBuilder.create()
                        .texOffs(28, 16).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.5F, -17.0F, -1.0F, -0.4F, -0.25F, -0.9F));

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create()
                        .texOffs(48, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(56, 16).addBox(-0.5F, 11.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-1.0F, -8.0F, 0.0F, 0.12F, 0.08F, 0.22F));

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create()
                        .texOffs(48, 34).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(56, 34).addBox(-0.5F, 10.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.0F, -8.0F, 0.5F, -0.06F, -0.04F, -0.14F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(PurpleMonsterSecondEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        this.head.yRot = 0.55F + netHeadYaw * Mth.DEG_TO_RAD * 0.35F;
        this.head.xRot = 0.22F + headPitch * Mth.DEG_TO_RAD * 0.25F;

        float sway = Mth.sin(ageInTicks * 0.08F) * 0.06F;
        float swaySlow = Mth.sin(ageInTicks * 0.05F + 1.2F) * 0.05F;
        this.root.zRot = sway * 0.35F;
        this.silk.zRot = -sway * 0.4F;
        this.leftArm.zRot = 1.95F + sway;
        this.rightArm.zRot = -2.25F - swaySlow;
        this.leftLeg.zRot = 0.22F + swaySlow;
        this.rightLeg.zRot = -0.14F - sway;
        this.midLeftLimb.zRot = 1.05F + sway * 0.6F;
        this.midRightLimb.zRot = -0.9F - swaySlow * 0.6F;
        this.body.zRot = 0.1F + sway * 0.25F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        this.root.render(poseStack, vertexConsumer, light, overlay, color);
    }
}

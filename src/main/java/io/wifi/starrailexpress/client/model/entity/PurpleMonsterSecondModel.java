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
 * 紫怪第二形态：小方头 + 细肢，部件全部挂在躯干上避免崩裂。
 */
public class PurpleMonsterSecondModel extends EntityModel<PurpleMonsterSecondEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(SRE.id("purple_monster_second"), "main");

    private final ModelPart root;
    private final ModelPart thorax;
    private final ModelPart head;
    private final ModelPart silk;
    private final ModelPart limbA;
    private final ModelPart limbB;
    private final ModelPart limbC;
    private final ModelPart limbD;
    private final ModelPart limbE;
    private final ModelPart limbF;

    public PurpleMonsterSecondModel(ModelPart root) {
        this.root = root.getChild("root");
        this.thorax = this.root.getChild("thorax");
        this.head = this.thorax.getChild("head");
        this.silk = this.thorax.getChild("silk");
        this.limbA = this.thorax.getChild("limb_a");
        this.limbB = this.thorax.getChild("limb_b");
        this.limbC = this.thorax.getChild("limb_c");
        this.limbD = this.thorax.getChild("limb_d");
        this.limbE = this.thorax.getChild("limb_e");
        this.limbF = this.thorax.getChild("limb_f");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition thorax = root.addOrReplaceChild("thorax",
                CubeListBuilder.create()
                        .texOffs(24, 0).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -18.0F, 0.0F));

        PartDefinition head = thorax.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -4.0F, 0.0F));
        head.addOrReplaceChild("eye",
                CubeListBuilder.create()
                        .texOffs(0, 40).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.4F)),
                PartPose.ZERO);

        thorax.addOrReplaceChild("silk",
                CubeListBuilder.create()
                        .texOffs(40, 4).addBox(-1.0F, -16.0F, -1.0F, 2.0F, 16.0F, 2.0F, new CubeDeformation(-0.4F)),
                PartPose.offset(0.0F, -4.0F, 0.0F));

        thorax.addOrReplaceChild("limb_a",
                CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 16.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-2.0F, -3.0F, 0.0F, 0.55F, 0.20F, 1.15F));
        thorax.addOrReplaceChild("limb_b",
                CubeListBuilder.create()
                        .texOffs(8, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 18.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.0F, -3.0F, 0.0F, -0.35F, -0.15F, -1.35F));
        thorax.addOrReplaceChild("limb_c",
                CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 14.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-1.0F, 0.0F, 1.0F, 0.85F, 0.40F, 0.70F));
        thorax.addOrReplaceChild("limb_d",
                CubeListBuilder.create()
                        .texOffs(24, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 15.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(1.0F, 0.0F, -1.0F, -0.55F, -0.25F, -0.75F));
        thorax.addOrReplaceChild("limb_e",
                CubeListBuilder.create()
                        .texOffs(32, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 14.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-1.0F, 3.0F, 0.0F, 0.20F, 0.05F, 0.25F));
        thorax.addOrReplaceChild("limb_f",
                CubeListBuilder.create()
                        .texOffs(40, 22).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 13.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(1.0F, 3.0F, 0.0F, -0.10F, -0.05F, -0.20F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(PurpleMonsterSecondEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.25F;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD * 0.20F;

        float sway = Mth.sin(ageInTicks * 0.06F) * 0.04F;
        float swayB = Mth.sin(ageInTicks * 0.045F + 1.1F) * 0.03F;
        this.thorax.zRot = sway * 0.4F;
        this.silk.zRot = -sway * 0.3F;
        this.limbA.zRot = 1.15F + sway;
        this.limbB.zRot = -1.35F - swayB;
        this.limbC.zRot = 0.70F + swayB;
        this.limbD.zRot = -0.75F - sway;
        this.limbE.zRot = 0.25F + swayB * 0.5F;
        this.limbF.zRot = -0.20F - sway * 0.5F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        this.root.render(poseStack, vertexConsumer, light, overlay, color);
    }
}

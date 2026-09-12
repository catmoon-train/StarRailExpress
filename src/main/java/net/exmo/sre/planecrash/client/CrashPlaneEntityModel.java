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

package net.exmo.sre.planecrash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.SRE;
import net.exmo.sre.planecrash.CrashPlaneEntity;
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
 * 窄体客机：机头朝 +Z（实体正前方），Y+ 朝上，尾翼在 -Z。
 */
public class CrashPlaneEntityModel extends EntityModel<CrashPlaneEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(SRE.id("crash_plane"), "main");

    private final ModelPart root;

    public CrashPlaneEntityModel(ModelPart root) {
        this.root = root.getChild("root");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition partdefinition = mesh.getRoot();
        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        CubeDeformation none = new CubeDeformation(0.0F);

        root.addOrReplaceChild("fuselage", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5.0F, -5.0F, -38.0F, 10.0F, 10.0F, 76.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("window_belt", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-5.15F, -1.2F, -30.0F, 10.3F, 2.4F, 60.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("fuselage_front", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, -4.5F, 38.0F, 9.0F, 9.0F, 14.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create()
                .texOffs(0, 220).addBox(-3.5F, -3.5F, 52.0F, 7.0F, 7.0F, 10.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("nose_tip", CubeListBuilder.create()
                .texOffs(0, 220).addBox(-2.0F, -2.0F, 62.0F, 4.0F, 5.0F, 6.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("cockpit", CubeListBuilder.create()
                .texOffs(0, 160).addBox(-3.6F, 1.4F, 46.0F, 7.2F, 4.2F, 11.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("fuselage_rear", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, -4.5F, -52.0F, 9.0F, 9.0F, 14.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("tail_cone", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -3.0F, -64.0F, 6.0F, 7.0F, 12.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("belly", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -7.0F, -18.0F, 6.0F, 2.5F, 36.0F, none),
                PartPose.ZERO);

        root.addOrReplaceChild("left_wing_in", CubeListBuilder.create()
                .texOffs(0, 80).addBox(5.0F, -1.2F, -8.0F, 30.0F, 2.2F, 22.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("left_wing_mid", CubeListBuilder.create()
                .texOffs(0, 80).addBox(34.0F, -0.8F, -16.0F, 26.0F, 1.8F, 18.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("left_wing_out", CubeListBuilder.create()
                .texOffs(0, 80).addBox(59.0F, -0.4F, -24.0F, 22.0F, 1.4F, 14.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("left_winglet", CubeListBuilder.create()
                .texOffs(0, 188).addBox(79.0F, -0.2F, -26.0F, 2.0F, 9.0F, 7.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("right_wing_in", CubeListBuilder.create()
                .texOffs(0, 80).addBox(-35.0F, -1.2F, -8.0F, 30.0F, 2.2F, 22.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("right_wing_mid", CubeListBuilder.create()
                .texOffs(0, 80).addBox(-60.0F, -0.8F, -16.0F, 26.0F, 1.8F, 18.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("right_wing_out", CubeListBuilder.create()
                .texOffs(0, 80).addBox(-81.0F, -0.4F, -24.0F, 22.0F, 1.4F, 14.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("right_winglet", CubeListBuilder.create()
                .texOffs(0, 188).addBox(-81.0F, -0.2F, -26.0F, 2.0F, 9.0F, 7.0F, none),
                PartPose.ZERO);

        root.addOrReplaceChild("left_pylon", CubeListBuilder.create()
                .texOffs(0, 120).addBox(20.5F, -7.0F, -2.0F, 3.0F, 6.0F, 8.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("left_engine", CubeListBuilder.create()
                .texOffs(0, 120).addBox(17.5F, -11.0F, -6.0F, 9.0F, 8.0F, 18.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("left_intake", CubeListBuilder.create()
                .texOffs(40, 120).addBox(18.5F, -10.0F, 12.0F, 7.0F, 6.0F, 4.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("right_pylon", CubeListBuilder.create()
                .texOffs(0, 120).addBox(-23.5F, -7.0F, -2.0F, 3.0F, 6.0F, 8.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("right_engine", CubeListBuilder.create()
                .texOffs(0, 120).addBox(-26.5F, -11.0F, -6.0F, 9.0F, 8.0F, 18.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("right_intake", CubeListBuilder.create()
                .texOffs(40, 120).addBox(-25.5F, -10.0F, 12.0F, 7.0F, 6.0F, 4.0F, none),
                PartPose.ZERO);

        root.addOrReplaceChild("tail_fin", CubeListBuilder.create()
                .texOffs(0, 188).addBox(-1.1F, 3.0F, -62.0F, 2.2F, 30.0F, 18.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("tail_fin_cap", CubeListBuilder.create()
                .texOffs(0, 188).addBox(-1.0F, 30.0F, -58.0F, 2.0F, 6.0F, 10.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("left_stab", CubeListBuilder.create()
                .texOffs(0, 80).addBox(1.0F, 8.0F, -60.0F, 24.0F, 1.6F, 13.0F, none),
                PartPose.ZERO);
        root.addOrReplaceChild("right_stab", CubeListBuilder.create()
                .texOffs(0, 80).addBox(-25.0F, 8.0F, -60.0F, 24.0F, 1.6F, 13.0F, none),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public void setupAnim(CrashPlaneEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        root.zRot = Mth.sin(ageInTicks * 0.10F) * 0.03F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        root.render(poseStack, vertexConsumer, light, overlay, color);
    }
}

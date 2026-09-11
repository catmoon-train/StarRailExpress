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
 * 客运飞机体：机头朝 -Z，机翼沿 X，尾翼在 +Z。
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

        root.addOrReplaceChild("fuselage", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0F, -6.0F, -40.0F, 12.0F, 12.0F, 72.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create()
                .texOffs(0, 84).addBox(-5.0F, -5.0F, -52.0F, 10.0F, 10.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("cockpit", CubeListBuilder.create()
                .texOffs(96, 0).addBox(-4.0F, 3.0F, -38.0F, 8.0F, 5.0F, 14.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("tail_cone", CubeListBuilder.create()
                .texOffs(44, 84).addBox(-5.0F, -5.0F, 32.0F, 10.0F, 10.0F, 16.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("left_wing", CubeListBuilder.create()
                .texOffs(0, 48).addBox(6.0F, -2.0F, -10.0F, 50.0F, 2.0F, 18.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("right_wing", CubeListBuilder.create()
                .texOffs(0, 68).addBox(-56.0F, -2.0F, -10.0F, 50.0F, 2.0F, 18.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("left_engine", CubeListBuilder.create()
                .texOffs(96, 20).addBox(18.0F, -8.0F, -6.0F, 7.0F, 7.0F, 16.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("right_engine", CubeListBuilder.create()
                .texOffs(96, 44).addBox(-25.0F, -8.0F, -6.0F, 7.0F, 7.0F, 16.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("tail_fin", CubeListBuilder.create()
                .texOffs(96, 68).addBox(-1.0F, 5.0F, 34.0F, 2.0F, 24.0F, 16.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("left_stab", CubeListBuilder.create()
                .texOffs(0, 108).addBox(1.0F, 10.0F, 38.0F, 20.0F, 2.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("right_stab", CubeListBuilder.create()
                .texOffs(64, 108).addBox(-21.0F, 10.0F, 38.0F, 20.0F, 2.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("gear_front", CubeListBuilder.create()
                .texOffs(120, 68).addBox(-1.0F, -13.0F, -16.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("gear_left", CubeListBuilder.create()
                .texOffs(120, 80).addBox(7.0F, -13.0F, 6.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        root.addOrReplaceChild("gear_right", CubeListBuilder.create()
                .texOffs(120, 92).addBox(-9.0F, -13.0F, 6.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(CrashPlaneEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        root.zRot = Mth.sin(ageInTicks * 0.12F) * 0.035F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        root.render(poseStack, vertexConsumer, light, overlay, color);
    }
}

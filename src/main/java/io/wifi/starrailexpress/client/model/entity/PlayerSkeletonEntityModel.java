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

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.HumanoidArm;

@Environment(EnvType.CLIENT)
public class PlayerSkeletonEntityModel<T extends PlayerBodyEntity> extends HumanoidModel<T> {
    public PlayerSkeletonEntityModel(ModelPart modelPart) {
        super(modelPart);
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition modelPartData = modelData.getRoot();
        addLimbs(modelPartData);
        return LayerDefinition.create(modelData, 64, 32);
    }

    protected static void addLimbs(PartDefinition data) {
        data.addOrReplaceChild(
                PartNames.HEAD,
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.1F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );
        data.addOrReplaceChild(
                PartNames.RIGHT_ARM, CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -1.9F, -1.0F, 2.0F, 12.0F, 2.0F), PartPose.offset(-5.0F, 2.0F, 0.0F)
        );
        data.addOrReplaceChild(
                PartNames.LEFT_ARM,
                CubeListBuilder.create().texOffs(40, 16).mirror().addBox(0.0F, -1.9F, -1.0F, 2.0F, 12.0F, 2.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F)
        );
        data.addOrReplaceChild(
                PartNames.RIGHT_LEG, CubeListBuilder.create().texOffs(0, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F), PartPose.offset(-2.0F, 12.0F, 0.0F)
        );
        data.addOrReplaceChild(
                PartNames.LEFT_LEG,
                CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F),
                PartPose.offset(2.0F, 12.0F, 0.0F)
        );
    }

    public void animateModel(T mobEntity, float f, float g, float h) {
        this.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        this.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.prepareMobModel(mobEntity, f, g, h);
    }

    @Override
    public void translateToHand(HumanoidArm arm, PoseStack matrices) {
        float f = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        ModelPart modelPart = this.getArm(arm);
        modelPart.x += f;
        modelPart.translateAndRotate(matrices);
        modelPart.x -= f;
    }
}

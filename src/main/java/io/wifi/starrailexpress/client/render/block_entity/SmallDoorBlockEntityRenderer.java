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

package io.wifi.starrailexpress.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.animation.SmallDoorAnimations;
import io.wifi.starrailexpress.client.model.TMMModelLayers;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class SmallDoorBlockEntityRenderer extends AnimatableBlockEntityRenderer<SmallDoorBlockEntity> {

    private final ResourceLocation texture;
    private final ModelPart part;

    public SmallDoorBlockEntityRenderer(ResourceLocation texture, BlockEntityRendererProvider.Context ctx) {
        this.texture = texture;
        this.part = ctx.bakeLayer(TMMModelLayers.SMALL_DOOR);
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition modelPartData = modelData.getRoot();
        modelPartData.addOrReplaceChild("Door", CubeListBuilder.create().texOffs(28, 56).addBox(-8.0F, -2.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(28, 60).addBox(-8.0F, -32.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-6.0F, -30.0F, 0.0F, 12.0F, 28.0F, 0.0F, new CubeDeformation(0.01F))
                .texOffs(0, 34).addBox(-8.0F, -30.0F, -1.0F, 2.0F, 28.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(8, 34).addBox(6.0F, -30.0F, -1.0F, 2.0F, 28.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(28, 0).addBox(-8.0F, -32.0F, -1.0F, 16.0F, 32.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));
        return LayerDefinition.create(modelData, 64, 64);
    }

    @Override
    public void render(SmallDoorBlockEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        matrices.translate(0.5f, 1.5f, 0.5f);
        matrices.scale(1, -1, 1);
        super.render(entity, tickDelta, matrices, vertexConsumers, light, overlay);
    }

    @Override
    public void setAngles(SmallDoorBlockEntity entity, float animationProgress) {
        this.parts().forEach(ModelPart::resetPose);
        this.part.setRotation(0, entity.getYaw() * Mth.DEG_TO_RAD, 0);
        final var anim = entity.isOpen() ? SmallDoorAnimations.OPEN : SmallDoorAnimations.CLOSE;
        if (this.isAnimationActive(entity.state, anim, animationProgress)) {
            this.animate(entity.state, anim, animationProgress);
        } else {
            this.applyFinalPose(anim);
        }
    }

    @Override
    public ResourceLocation getTexture(SmallDoorBlockEntity entity, float tickDelta) {
        return this.texture;
    }

    @Override
    public int getAge(SmallDoorBlockEntity entity) {
        return entity.getAge();
    }

    @Override
    public ModelPart root() {
        return this.part;
    }
}

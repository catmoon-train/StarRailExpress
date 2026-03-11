package io.wifi.starrailexpress.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.client.animation.SmallDoorAnimations;
import io.wifi.starrailexpress.client.model.TMMModelLayers;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
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
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.part.setRotation(0, entity.getYaw() * Mth.DEG_TO_RAD, 0);
        this.animate(entity.state, entity.isOpen() ? SmallDoorAnimations.OPEN : SmallDoorAnimations.CLOSE, animationProgress);
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

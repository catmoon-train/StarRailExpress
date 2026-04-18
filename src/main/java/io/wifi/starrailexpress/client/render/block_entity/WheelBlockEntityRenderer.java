package io.wifi.starrailexpress.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.model.TMMModelLayers;
import io.wifi.starrailexpress.contents.block_entity.WheelBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class WheelBlockEntityRenderer extends AnimatableBlockEntityRenderer<WheelBlockEntity> {

    private final ResourceLocation texture;
    private final ModelPart part;

    public WheelBlockEntityRenderer(ResourceLocation texture, BlockEntityRendererProvider.Context ctx) {
        this.texture = texture;
        this.part = ctx.bakeLayer(TMMModelLayers.WHEEL);
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition modelPartData = modelData.getRoot();
        PartDefinition all = modelPartData.addOrReplaceChild("all",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.6F, -16.0F, -16.0F, 1.0F, 32.0F, 32.0F, new CubeDeformation(-0.3F))
                        .texOffs(0, 0).mirror()
                        .addBox(-0.4F, -16.0F, -16.0F, 1.0F, 32.0F, 32.0F, new CubeDeformation(-0.3F)).mirror(false),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition bone = all.addOrReplaceChild("bone", CubeListBuilder.create(),
                PartPose.offset(-0.5F, 0.0F, 0.0F));

        PartDefinition cube_r1 = bone
                .addOrReplaceChild("cube_r1",
                        CubeListBuilder.create().texOffs(89, 56).addBox(-2.0F, -4.0F, -4.0F, 4.0F, 8.0F, 8.0F,
                                new CubeDeformation(0.0F)),
                        PartPose.offsetAndRotation(0.5F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

        PartDefinition normalsides = all.addOrReplaceChild("normalsides",
                CubeListBuilder.create().texOffs(32, 64).mirror()
                        .addBox(-2.0F, -4.0F, -6.0F, 4.0F, 4.0F, 12.0F, new CubeDeformation(-0.02F)).mirror(false)
                        .texOffs(0, 64).mirror()
                        .addBox(-2.0F, -32.0F, -6.0F, 4.0F, 4.0F, 12.0F, new CubeDeformation(-0.02F)).mirror(false)
                        .texOffs(0, 80).mirror()
                        .addBox(-2.0F, -22.0F, -16.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(-0.02F)).mirror(false)
                        .texOffs(16, 80).addBox(-2.0F, -22.0F, 12.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(-0.02F)),
                PartPose.offset(0.0F, 16.0F, 0.0F));

        PartDefinition angledsides = normalsides.addOrReplaceChild("angledsides", CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition cube_r2 = angledsides.addOrReplaceChild("cube_r2",
                CubeListBuilder.create().texOffs(66, 18).mirror()
                        .addBox(-2.0F, 0.0F, 0.0F, 4.0F, 14.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(0.0F, -10.0F, -16.0F, 0.7854F, 0.0F, 0.0F));

        PartDefinition cube_r3 = angledsides.addOrReplaceChild("cube_r3",
                CubeListBuilder.create().texOffs(66, 36).addBox(-2.0F, -14.0F, -4.0F, 4.0F, 14.0F, 4.0F,
                        new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -22.0F, 16.0F, 0.7854F, 0.0F, 0.0F));

        PartDefinition cube_r4 = angledsides.addOrReplaceChild("cube_r4",
                CubeListBuilder.create().texOffs(66, 36).addBox(-2.0F, 0.0F, -4.0F, 4.0F, 14.0F, 4.0F,
                        new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -10.0F, 16.0F, -0.7854F, 0.0F, 0.0F));

        PartDefinition cube_r5 = angledsides.addOrReplaceChild("cube_r5",
                CubeListBuilder.create().texOffs(66, 18).mirror()
                        .addBox(-2.0F, -14.0F, 0.0F, 4.0F, 14.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(0.0F, -22.0F, -16.0F, -0.7854F, 0.0F, 0.0F));
        return LayerDefinition.create(modelData, 128, 128);
    }

    private static final double MAX_RENDER_DISTANCE_SQ = 16.0 * 16.0; // 16个方块的距离

    /**
     * 检查是否应该渲染该方块实体
     * 
     * @param entity 方块实体
     * @return 是否应该渲染
     */
    private boolean shouldRender(WheelBlockEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // 如果没有玩家或世界为空，则不渲染
        if (player == null || entity.getLevel() == null) {
            return false;
        }

        // 计算玩家与方块实体之间的距离平方
        double distanceSq = player.distanceToSqr(
                entity.getBlockPos().getX() + 0.5,
                entity.getBlockPos().getY() + 0.5,
                entity.getBlockPos().getZ() + 0.5);

        // 如果距离超过最大渲染距离，则不渲染
        return distanceSq <= MAX_RENDER_DISTANCE_SQ;
    }

    @Override
    public void render(WheelBlockEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers,
            int light, int overlay) {
        // 检查渲染距离
        if (!shouldRender(entity)) {
            return;
        }
        matrices.translate(0, 0.3f, .5f);
        matrices.mulPose(Axis.ZP
                .rotationDegrees((SREClient.trainComponent.getTime() + tickDelta) * (SREClient.getTrainSpeed() * .9f)));
        super.render(entity, tickDelta, matrices, vertexConsumers, light, overlay);
    }

    @Override
    public void setAngles(WheelBlockEntity entity, float animationProgress) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.part.setRotation(0, entity.getYaw() * Mth.DEG_TO_RAD, 0);
    }

    @Override
    public ResourceLocation getTexture(WheelBlockEntity entity, float tickDelta) {
        return this.texture;
    }

    @Override
    public ModelPart root() {
        return this.part;
    }
}

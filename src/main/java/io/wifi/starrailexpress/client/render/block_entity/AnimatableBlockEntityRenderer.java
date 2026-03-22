package io.wifi.starrailexpress.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Function;

public abstract class AnimatableBlockEntityRenderer<T extends BlockEntity> extends HierarchicalModel<Entity> implements BlockEntityRenderer<T> {

    public AnimatableBlockEntityRenderer() {
        super();
    }

    public AnimatableBlockEntityRenderer(Function<ResourceLocation, RenderType> layerFactory) {
        super(layerFactory);
    }

    @Override
    public void render(T entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        this.setAngles(entity, this.getAge(entity) + tickDelta);
        this.renderPart(entity, tickDelta, matrices, vertexConsumers, light, overlay);
    }

    public void renderPart(T entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        this.root().render(matrices, vertexConsumers.getBuffer(this.renderType.apply(this.getTexture(entity, tickDelta))), light, overlay);
    }

    public int getAge(T entity) {
        return entity.getLevel() == null ? 0 : (int) entity.getLevel().getGameTime();
    }

    public abstract void setAngles(T entity, float animationProgress);

    public abstract ResourceLocation getTexture(T entity, float tickDelta);

    @Override
    public final void setupAnim(Entity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        throw new AssertionError();
    }

    @Override
    public final void prepareMobModel(Entity entity, float limbAngle, float limbDistance, float tickDelta) {
        throw new AssertionError();
    }
}

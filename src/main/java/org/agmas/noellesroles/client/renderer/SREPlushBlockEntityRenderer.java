package org.agmas.noellesroles.client.renderer;

import org.agmas.noellesroles.content.block_entity.SREPlushBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SREPlushBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private final BlockRenderDispatcher renderer;

    public SREPlushBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.renderer = ctx.getBlockRenderDispatcher();
    }

    @Override
    public void render(T blockEntity, float delta, PoseStack poseStack, MultiBufferSource consumer, int light,
            int overlay) {
        poseStack.pushPose();
        var squish = blockEntity instanceof SREPlushBlockEntity plushie ? plushie.squash : 0;
        var lastSquish = squish * 3;
        var squash = (float) Math.pow(
                (double) 1.0F - (double) 1.0F / ((double) 1.0F + Mth.lerp((double) delta, lastSquish, squish)),
                (double) 2.0F);
        poseStack.scale(1, 1F - squash, 1);
        poseStack.translate(0.5, 0, 0.5);
        poseStack.scale(1F + squash / 2F, 1, 1F + squash / 2F);
        poseStack.translate(-0.5, 0, -0.5);
        var state = blockEntity.getBlockState();
        var bakedModel = this.renderer.getBlockModel(state);
        this.renderer.getModelRenderer().renderModel(poseStack.last(),
                consumer.getBuffer(ItemBlockRenderTypes.getRenderType(state, false)), state, bakedModel, 1F, 1F, 1F,
                light, overlay);
        poseStack.popPose();
    }

}

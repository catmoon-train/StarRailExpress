package io.wifi.starrailexpress.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.doctor4t.ratatouille.client.lib.render.helpers.Easing;
import dev.doctor4t.ratatouille.mixin.client.BlockRenderManagerAccessor;
import io.wifi.starrailexpress.block.entity.HornBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
public class HornBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private final BlockRenderDispatcher renderManager;

    public HornBlockEntityRenderer(BlockEntityRendererProvider.@NotNull Context ctx) {
        this.renderManager = ctx.getBlockRenderDispatcher();
    }

    public void render(@NotNull T entity, float tickDelta, @NotNull PoseStack matrices, @NotNull MultiBufferSource vertexConsumers, int light, int overlay) {
        matrices.pushPose();
        float pull = Easing.CUBIC_IN.ease((entity instanceof HornBlockEntity plushie ? (float) Mth.lerp(tickDelta, plushie.prevPull, plushie.pull) : 0), 0, 1, 1) / 2f;
        matrices.translate(0, -pull, 0);
        BlockState state = entity.getBlockState();
        ((BlockRenderManagerAccessor) this.renderManager).getModelRenderer().renderModel(matrices.last(), vertexConsumers.getBuffer(ItemBlockRenderTypes.getRenderType(state, false)), state, this.renderManager.getBlockModel(state), 0xFF, 0xFF, 0xFF, light, overlay);

        matrices.pushPose();
        BlockState chain = Blocks.CHAIN.defaultBlockState();
        matrices.translate(0, 1, 0);
        ((BlockRenderManagerAccessor) this.renderManager).getModelRenderer().renderModel(matrices.last(), vertexConsumers.getBuffer(ItemBlockRenderTypes.getRenderType(chain, false)), chain, this.renderManager.getBlockModel(chain), 0xFF, 0xFF, 0xFF, light, overlay);
        matrices.translate(0, 1, 0);
        ((BlockRenderManagerAccessor) this.renderManager).getModelRenderer().renderModel(matrices.last(), vertexConsumers.getBuffer(ItemBlockRenderTypes.getRenderType(chain, false)), chain, this.renderManager.getBlockModel(chain), 0xFF, 0xFF, 0xFF, light, overlay);
        matrices.popPose();

        matrices.popPose();
    }
}
package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.wifi.starrailexpress.SREClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import org.agmas.noellesroles.content.block.scene.BreakingBridgeBlock;
import org.agmas.noellesroles.content.block_entity.scene.BreakingBridgeBlockEntity;
import org.jetbrains.annotations.NotNull;

public class BreakingBridgeBlockEntityRenderer implements BlockEntityRenderer<BreakingBridgeBlockEntity> {
    private final BlockRenderDispatcher blockRenderer;
    private static final double MAX_RENDER_DISTANCE_SQ = 64.0 * 64.0; // 48个方块的距离
    private static final double MAX_RENDER_DISTANCE_LQ = 32.0 * 32.0; // 32个方块的距离

    public BreakingBridgeBlockEntityRenderer(BlockEntityRendererProvider.@NotNull Context ctx) {
        ctx.getBlockEntityRenderDispatcher();
        blockRenderer = ctx.getBlockRenderDispatcher();
    }

    /**
     * 检查是否应该渲染该方块实体
     * 
     * @param entity 方块实体
     * @return 是否应该渲染
     */
    private boolean shouldRender(@NotNull BreakingBridgeBlockEntity entity) {
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
        if (SREClientConfig.instance().ultraPerfMode) {
            return distanceSq <= MAX_RENDER_DISTANCE_LQ;
        }
        return distanceSq <= MAX_RENDER_DISTANCE_SQ;
    }

    @Override
    public void render(@NotNull BreakingBridgeBlockEntity entity, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light, int overlay) {
        if (!shouldRender(entity)) {
            return;
        }
        int setstage = entity.breakingStage; // 假设返回 0~10
        int stage = setstage;
        if (entity.nowTime >= 0 && entity.breakingTime > 0) {
            float percent = (float) entity.nowTime / (float) entity.breakingTime;
            stage = (int) ((float) setstage + percent * (10f - setstage));
        }
        if (stage < 0 || stage > 10) {
            stage = 0;
        }
        var blockState = entity.getBlockState();
        if (blockState.getOptionalValue(BreakingBridgeBlock.BROKEN).orElse(false)) {
            return;
        }
        BlockState stateToRender = entity.displayState != null ? entity.displayState : blockState;
        if (stateToRender != null) {

            RenderShape renderShape = blockState.getRenderShape();
            switch (renderShape) {
                case MODEL:
                    blockRenderer.renderSingleBlock(stateToRender, matrices, vertexConsumers,
                            light, overlay);
                    break;
                default:
                    BakedModel model = blockRenderer.getBlockModel(stateToRender);
                    // 获取 VertexConsumer
                    // 渲染破坏纹理（renderBreaking = true）
                    long seed = stateToRender.getSeed(entity.getBlockPos());

                    for (var renderType : RenderType.chunkBufferLayers())
                        if (ItemBlockRenderTypes.getChunkRenderType(stateToRender) == renderType)
                            blockRenderer.getModelRenderer().tesselateBlock(entity.getLevel(), model, stateToRender,
                                    entity.getBlockPos(), matrices, vertexConsumers.getBuffer(renderType), false,
                                    RandomSource.create(), seed, OverlayTexture.NO_OVERLAY);
                    break;
            }

            {
                if (stage > 0 && stage < 10) { // 0 无裂纹，10 完全破坏，可根据需要调整
                    // 纹理索引 0~9
                    int textureIndex = Math.min(stage, 9);
                    RenderType crack = ModelBakery.DESTROY_TYPES.get(textureIndex);
                    // 2.2 使用 RenderType.crack() 获得正确的 VertexConsumer（透明混合）
                    VertexConsumer crackConsumer = vertexConsumers
                            .getBuffer(crack);
                    SheetedDecalTextureGenerator decalBuffer = new SheetedDecalTextureGenerator(
                            crackConsumer,
                            matrices.last(),
                            1.0f);
                    // 渲染破坏纹理（内部使用 NO_OVERLAY，纹理本身已包含破损图案）
                    blockRenderer.renderBreakingTexture(
                            stateToRender,
                            entity.getBlockPos(),
                            entity.getLevel(),
                            matrices,
                            decalBuffer);
                }
            }
        }
    }
}

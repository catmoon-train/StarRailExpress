package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

import org.agmas.noellesroles.content.block_entity.scene.BreakingBridgeBlockEntity;
import org.jetbrains.annotations.NotNull;

public class BreakingBridgeBlockEntityRenderer implements BlockEntityRenderer<BreakingBridgeBlockEntity> {
    // 渲染距离限制（方块数的平方）
    private final BlockRenderDispatcher blockRenderer;
    private static final double MAX_RENDER_DISTANCE_SQ = 8.0 * 8.0; // 16个方块的距离

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
        return distanceSq <= MAX_RENDER_DISTANCE_SQ;
    }

    @Override
    public void render(@NotNull BreakingBridgeBlockEntity entity, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light, int overlay) {
        if (!shouldRender(entity)) {
            return;
        }
        BlockState stateToRender = entity.displayState != null ? entity.displayState : entity.getBlockState();
        if (stateToRender != null) {
            blockRenderer.renderSingleBlock(stateToRender, matrices, vertexConsumers, light, overlay);
        }
    }
}

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

package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.wifi.starrailexpress.SREClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
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
import org.joml.Matrix4f;

import org.agmas.noellesroles.content.block.scene.BreakingBridgeBlock;
import org.agmas.noellesroles.content.block_entity.scene.BreakingBridgeBlockEntity;
import org.jetbrains.annotations.NotNull;

public class BreakingBridgeBlockEntityRenderer implements BlockEntityRenderer<BreakingBridgeBlockEntity> {
    private final BlockRenderDispatcher blockRenderer;
    private static final double MAX_RENDER_DISTANCE_SQ = 64.0 * 64.0; // 48个方块的距离
    private static final double MAX_RENDER_DISTANCE_LQ = 32.0 * 32.0; // 32个方块的距离
    /** 共享随机源：避免每方块每帧分配 RandomSource，seed 每次重置保证纹理一致。 */
    private static final RandomSource RANDOM = RandomSource.create();

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
        BlockState display = entity.displayState;
        BlockPos blockPos = entity.getBlockPos();

        // displayState == null：区块已经渲染了实际模型，方块实体无需重复渲染
        // （消除每帧的双重渲染）；只有假渲染（displayState != null）才需要
        // 由方块实体绘制显示模型。
        BlockState stateToRender = display != null ? display : blockState;
        if (display != null && !stateToRender.isAir()) {
            renderDisplayModel(entity, stateToRender, matrices, vertexConsumers, light);
        }

        if (stage > 0 && stage < 10) { // 0 无裂纹，10 完全破坏，可根据需要调整
            BlockState crackTarget = display != null ? display : blockState;
            if (!crackTarget.isAir()) {
                // 纹理索引 0~9
                int textureIndex = Math.min(stage, 9);
                RenderType crack = ModelBakery.DESTROY_TYPES.get(textureIndex);
                // 2.2 使用 RenderType.crack() 获得正确的 VertexConsumer（透明混合）
                VertexConsumer crackConsumer = vertexConsumers.getBuffer(crack);
                SheetedDecalTextureGenerator decalBuffer = new SheetedDecalTextureGenerator(
                        crackConsumer, matrices.last(), 1.0f);
                // 渲染破坏纹理（内部使用 NO_OVERLAY，纹理本身已包含破损图案）
                blockRenderer.renderBreakingTexture(
                        crackTarget, blockPos, entity.getLevel(), matrices, decalBuffer);
            }
        }
    }

    /**
     * 渲染假显示模型。首次（或 displayState/光照变化）用单位姿态捕获块局部顶点
     * 存入实体缓存（堆内存，无原生生命周期问题）；之后每帧仅按相机平移重放，
     * 批量写入 bufferSource，避免每帧完整 tesselateBlock（AO/光照计算）。
     */
    private void renderDisplayModel(BreakingBridgeBlockEntity entity, BlockState display,
            PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        if (display != entity.cachedDisplayState || light != entity.cachedLight
                || entity.cachedVertices == null) {
            entity.cachedVertices = captureDisplayModel(entity, display);
            entity.cachedDisplayState = display;
            entity.cachedLight = light;
        }
        RenderType renderType = ItemBlockRenderTypes.getChunkRenderType(display);
        VertexConsumer consumer = vertexConsumers.getBuffer(renderType);
        Matrix4f pose = matrices.last().pose();
        for (double[] v : entity.cachedVertices) {
            consumer.addVertex(pose, (float) v[0], (float) v[1], (float) v[2])
                    .setColor((int) v[3])
                    .setUv((float) v[4], (float) v[5])
                    .setOverlay((int) v[6])
                    .setLight((int) v[7])
                    .setNormal((float) v[8], (float) v[9], (float) v[10]);
        }
    }

    /** 用单位姿态捕获一次显示模型的块局部顶点（含颜色/UV/光照/法线）。 */
    private java.util.List<double[]> captureDisplayModel(BreakingBridgeBlockEntity entity, BlockState display) {
        BakedModel model = blockRenderer.getBlockModel(display);
        long seed = display.getSeed(entity.getBlockPos());
        CaptureConsumer capture = new CaptureConsumer();
        RANDOM.setSeed(seed);
        blockRenderer.getModelRenderer().tesselateBlock(entity.getLevel(), model, display,
                entity.getBlockPos(), new PoseStack(), capture, false, RANDOM, seed,
                OverlayTexture.NO_OVERLAY);
        return capture.vertices;
    }

    /**
     * 捕获 tesselateBlock 写入的顶点：每顶点 11 个 double ——
     * [x,y,z, colorPacked, u,v, overlay, light, nx,ny,nz]。
     */
    private static final class CaptureConsumer implements VertexConsumer {
        final java.util.List<double[]> vertices = new java.util.ArrayList<>();
        private double[] cur;

        private void startVertex(float x, float y, float z) {
            cur = new double[11];
            cur[0] = x;
            cur[1] = y;
            cur[2] = z;
            vertices.add(cur);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            startVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            cur[3] = (r << 16) | (g << 8) | b | (a << 24);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            cur[4] = u;
            cur[5] = v;
            return this;
        }

        @Override
        public VertexConsumer setOverlay(int o) {
            cur[6] = o;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            cur[6] = (u & 0xFFFF) | (v << 16);
            return this;
        }

        @Override
        public VertexConsumer setLight(int l) {
            cur[7] = l;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            cur[7] = (u & 0xFFFF) | (v << 16);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            cur[8] = nx;
            cur[9] = ny;
            cur[10] = nz;
            return this;
        }
    }
}

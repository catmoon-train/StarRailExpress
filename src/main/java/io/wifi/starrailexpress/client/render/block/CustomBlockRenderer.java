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

package io.wifi.starrailexpress.client.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.customblock.CustomBlock;
import io.wifi.starrailexpress.customblock.CustomBlockData;
import io.wifi.starrailexpress.customblock.CustomBlockEntity;
import io.wifi.starrailexpress.customblock.CustomBlockLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 自定义方块渲染器。
 *
 * <p>
 * 外观三选一（见 {@link CustomBlockAppearance}）：资源包贴图 → 继承方块的烘焙模型 → 占位棋盘。
 * 三种情况都顺带画原版的挖掘裂纹（否则 {@code ENTITYBLOCK_ANIMATED} 的方块挖起来没有任何反馈）。
 *
 * <p>
 * <b>性能</b>：继承方块模型只在「展示状态或光照变化」时重新捕获顶点，之后每帧只做重放；
 * 距离超过 64 格（{@code ultraPerfMode} 下 32 格）直接不渲染。
 */
@Environment(EnvType.CLIENT)
public class CustomBlockRenderer implements BlockEntityRenderer<CustomBlockEntity> {

    private static final double MAX_RENDER_DISTANCE_SQ = 64.0D * 64.0D;
    private static final double MAX_RENDER_DISTANCE_SQ_LOW = 32.0D * 32.0D;

    private final BlockRenderDispatcher blockRenderer;

    public CustomBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(@NotNull CustomBlockEntity entity, float partialTick, @NotNull PoseStack poseStack,
            @NotNull MultiBufferSource buffers, int light, int overlay) {
        CustomBlockData data = entity.data();
        if (data == null) {
            CustomBlockAppearance.renderPlaceholder(poseStack, buffers, light, overlay);
            renderBreakingOverlay(entity, Blocks.STONE.defaultBlockState(), poseStack, buffers);
            return;
        }

        // ① 资源包贴图：整方块贴图，优先级最高
        ResourceLocation packTexture = CustomBlockAppearance.resolvePackTexture(data.packTexturePath);
        if (packTexture != null) {
            CustomBlockAppearance.renderTexturedCube(poseStack, buffers, packTexture, light, overlay);
            renderBreakingOverlay(entity, Blocks.STONE.defaultBlockState(), poseStack, buffers);
            return;
        }

        // ② 继承方块：捕获一次顶点后逐帧重放
        BlockState ownState = entity.getBlockState();
        BlockState inherited = CustomBlockLoader.inheritedState(data,
                ownState.getValue(CustomBlock.FACING), ownState.getValue(CustomBlock.WATERLOGGED));
        if (inherited == null) {
            CustomBlockAppearance.renderPlaceholder(poseStack, buffers, light, overlay);
            renderBreakingOverlay(entity, Blocks.STONE.defaultBlockState(), poseStack, buffers);
            return;
        }
        if (inherited != entity.cachedDisplayState || light != entity.cachedLight
                || entity.cachedVertices == null) {
            entity.cachedVertices = CustomBlockAppearance.captureModel(blockRenderer, entity.getLevel(), inherited,
                    entity.getBlockPos());
            entity.cachedDisplayState = inherited;
            entity.cachedLight = light;
        }
        CustomBlockAppearance.replay(poseStack, buffers, entity.cachedVertices, inherited);
        renderBreakingOverlay(entity, inherited, poseStack, buffers);
    }

    /**
     * 画挖掘裂纹。
     *
     * <p>
     * {@code ENTITYBLOCK_ANIMATED} 的方块不会走原版的破坏覆盖层，所以这里按「本地玩家正在挖
     * 这个方块」的客户端状态自己画一次（每帧最多一个方块，开销可忽略）。
     */
    private void renderBreakingOverlay(CustomBlockEntity entity, BlockState crackTarget, PoseStack poseStack,
            MultiBufferSource buffers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || !minecraft.gameMode.isDestroying()) {
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(entity.getBlockPos())) {
            return;
        }
        int stage = minecraft.gameMode.getDestroyStage();
        if (stage < 0) {
            return;
        }
        BlockState target = crackTarget == null || crackTarget.isAir() ? Blocks.STONE.defaultBlockState() : crackTarget;
        RenderType crackType = ModelBakery.DESTROY_TYPES.get(Math.min(stage, 9));
        VertexConsumer consumer = buffers.getBuffer(crackType);
        SheetedDecalTextureGenerator decal = new SheetedDecalTextureGenerator(consumer, poseStack.last(), 1.0F);
        blockRenderer.renderBreakingTexture(target, entity.getBlockPos(), entity.getLevel(), poseStack, decal);
    }

    @Override
    public boolean shouldRender(@NotNull CustomBlockEntity entity, @NotNull Vec3 cameraPos) {
        double distanceSqr = Vec3.atCenterOf(entity.getBlockPos()).distanceToSqr(cameraPos);
        double limit = SREClientConfig.instance().ultraPerfMode ? MAX_RENDER_DISTANCE_SQ_LOW : MAX_RENDER_DISTANCE_SQ;
        return distanceSqr <= limit;
    }
}

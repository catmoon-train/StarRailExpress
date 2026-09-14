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
import io.wifi.starrailexpress.client.render.block_entity.DisplayBlockRenderSupport.InterpolationState;
import io.wifi.starrailexpress.content.block_entity.BlockDisplayBlockEntity;
import io.wifi.starrailexpress.content.block_entity.DisplayBlockEntityBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 方块展示方块的渲染器：按原版 {@code Display.BlockDisplay} 的规则在方块位置画一个方块模型，
 * 绘制流程与原版 {@code DisplayRenderer.BlockDisplayRenderer.renderInner} 一致。
 *
 * <p>方块状态按数据版本缓存（不像文本那样每帧解析一次 NBT），视野判定也不造临时 Vec3。
 */
public class BlockDisplayBlockEntityRenderer implements BlockEntityRenderer<BlockDisplayBlockEntity> {

    public BlockDisplayBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BlockDisplayBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        BlockState displayed = blockEntity.getDisplayedBlockState();
        if (displayed.isAir()) {
            return;
        }

        CompoundTag tag = blockEntity.getRawDisplayData();
        InterpolationState state = DisplayBlockRenderSupport.stateFor(blockEntity);
        state.update(tag, blockEntity.getDataRevision(), level.getGameTime(), partialTick);

        int light = DisplayBlockRenderSupport.resolveLight(state.packedBrightnessOverride(), packedLight);
        OutlineBufferSource outline = DisplayBlockRenderSupport.beginGlow(state.glowColor());
        MultiBufferSource buffer = DisplayBlockRenderSupport.glowSource(outline, bufferSource);

        poseStack.pushPose();
        // 先把原点挪到方块中心，变换（旋转/缩放）才会绕方块中心转；
        // 模型自身是以最小角为基准的，所以变换后再把原点挪回去，无变换时正好与方块重合。
        DisplayBlockRenderSupport.moveToBlockCenter(poseStack);
        DisplayBlockRenderSupport.applyDisplayTransform(poseStack, state.billboard(), state.currentTransformation());
        DisplayBlockRenderSupport.restoreModelOrigin(poseStack);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(displayed, poseStack, buffer, light,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        DisplayBlockRenderSupport.endGlow(outline);
    }

    @Override
    public boolean shouldRender(BlockDisplayBlockEntity blockEntity, Vec3 cameraPos) {
        return DisplayBlockRenderSupport.isWithinViewDistance(blockEntity.getBlockPos(), cameraPos,
                blockEntity.getRawDisplayData());
    }

    @Override
    public int getViewDistance() {
        return DisplayBlockEntityBase.MAX_RENDER_DISTANCE;
    }
}

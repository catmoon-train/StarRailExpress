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
import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.render.block_entity.DisplayBlockRenderSupport.InterpolationState;
import io.wifi.starrailexpress.content.block_entity.DisplayBlockEntityBase;
import io.wifi.starrailexpress.content.block_entity.ItemDisplayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 物品展示方块的渲染器：按原版 {@code Display.ItemDisplay} 的规则在方块位置画一个物品堆，
 * 绘制流程与原版 {@code DisplayRenderer.ItemDisplayRenderer.renderInner} 一致
 * （绕 Y 轴转 180°，再走 {@code ItemRenderer.renderStatic}）。
 */
public class ItemDisplayBlockEntityRenderer implements BlockEntityRenderer<ItemDisplayBlockEntity> {

    public ItemDisplayBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ItemDisplayBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        ItemStack stack = blockEntity.getDisplayedItem(level.registryAccess());
        if (stack.isEmpty()) {
            return;
        }

        CompoundTag tag = blockEntity.getRawDisplayData();
        InterpolationState state = DisplayBlockRenderSupport.stateFor(blockEntity);
        state.update(tag, blockEntity.getDataRevision(), level.getGameTime(), partialTick);

        int light = DisplayBlockRenderSupport.resolveLight(state.packedBrightnessOverride(), packedLight);
        OutlineBufferSource outline = DisplayBlockRenderSupport.beginGlow(state.glowColor());
        MultiBufferSource buffer = DisplayBlockRenderSupport.glowSource(outline, bufferSource);

        poseStack.pushPose();
        DisplayBlockRenderSupport.moveToBlockCenter(poseStack);
        DisplayBlockRenderSupport.applyDisplayTransform(poseStack, state.billboard(), state.currentTransformation());
        poseStack.mulPose(Axis.YP.rotation((float) Math.PI));
        // seed 用方块坐标，稳定且逐个方块不同（原版展示实体用的是实体 id）
        int seed = (int) Math.floorMod(blockEntity.getBlockPos().asLong(), 1024L);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, blockEntity.getItemTransform(), light,
                OverlayTexture.NO_OVERLAY, poseStack, buffer, level, seed);
        poseStack.popPose();

        DisplayBlockRenderSupport.endGlow(outline);
    }

    @Override
    public boolean shouldRender(ItemDisplayBlockEntity blockEntity, Vec3 cameraPos) {
        return DisplayBlockRenderSupport.isWithinViewDistance(blockEntity.getBlockPos(), cameraPos,
                blockEntity.getRawDisplayData());
    }

    @Override
    public int getViewDistance() {
        return DisplayBlockEntityBase.MAX_RENDER_DISTANCE;
    }
}

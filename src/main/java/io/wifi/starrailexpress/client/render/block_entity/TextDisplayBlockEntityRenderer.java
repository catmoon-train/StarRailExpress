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
import io.wifi.starrailexpress.content.block_entity.DisplayBlockEntityBase;
import io.wifi.starrailexpress.content.block_entity.TextDisplayBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 文本展示方块的渲染器：按原版 {@code Display.TextDisplay} 的规则在方块位置画文本，
 * 绘制流程与原版 {@code DisplayRenderer.TextDisplayRenderer.renderInner} 一致。
 *
 * <p>热路径上刻意不做多余分配：插值状态挂在方块实体自己身上，分行结果按数据版本缓存，
 * 朝向/亮度/发光颜色都只在数据变化时解析一次，视野判定也不用临时 Vec3。
 */
public class TextDisplayBlockEntityRenderer implements BlockEntityRenderer<TextDisplayBlockEntity> {

    public TextDisplayBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TextDisplayBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        CompoundTag tag = blockEntity.getRawDisplayData();
        InterpolationState state = DisplayBlockRenderSupport.stateFor(blockEntity);
        state.update(tag, blockEntity.getDataRevision(), level.getGameTime(), partialTick);

        Display.TextDisplay.CachedInfo cachedInfo = state.textInfo(blockEntity, level.registryAccess());
        if (cachedInfo.lines().isEmpty()) {
            // 没有可画的内容（空文本或 JSON 解析失败）就直接结束，别走后面一整套绘制。
            return;
        }

        int light = DisplayBlockRenderSupport.resolveLight(state.packedBrightnessOverride(), packedLight);
        OutlineBufferSource outline = DisplayBlockRenderSupport.beginGlow(state.glowColor());
        MultiBufferSource buffer = DisplayBlockRenderSupport.glowSource(outline, bufferSource);

        poseStack.pushPose();
        // 以方块中心为锚点：原版展示实体把内容锚在实体脚下，方块版按方块中心对齐才符合直觉
        DisplayBlockRenderSupport.moveToBlockCenter(poseStack);
        DisplayBlockRenderSupport.applyDisplayTransform(poseStack, state.billboard(), state.currentTransformation());
        DisplayBlockRenderSupport.renderText(cachedInfo, poseStack, buffer, light,
                state.currentTextOpacity(), state.currentBackground(), state.textFlags());
        poseStack.popPose();

        DisplayBlockRenderSupport.endGlow(outline);
    }

    @Override
    public boolean shouldRender(TextDisplayBlockEntity blockEntity, Vec3 cameraPos) {
        return DisplayBlockRenderSupport.isWithinViewDistance(blockEntity.getBlockPos(), cameraPos,
                blockEntity.getRawDisplayData());
    }

    @Override
    public int getViewDistance() {
        return DisplayBlockEntityBase.MAX_RENDER_DISTANCE;
    }
}

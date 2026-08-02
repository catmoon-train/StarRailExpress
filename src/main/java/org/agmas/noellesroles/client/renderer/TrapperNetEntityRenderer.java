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
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.agmas.noellesroles.content.entity.TrapperNetEntity;

/**
 * 捕网实体渲染器：以蜘蛛网方块模型渲染。
 *
 * <p>飞行中旋转翻滚增强投掷感；落地展开后放大平铺、停止旋转。
 */
public class TrapperNetEntityRenderer extends EntityRenderer<TrapperNetEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public TrapperNetEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.2f;
    }

    @Override
    public void render(TrapperNetEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {
        matrices.pushPose();
        if (entity.isLanded()) {
            // 展开：放大平铺在地
            matrices.scale(1.8f, 1.0f, 1.8f);
            matrices.translate(-0.5, 0.0, -0.5);
        } else {
            // 飞行：绕轴翻滚
            matrices.translate(0.0, 0.25, 0.0);
            matrices.mulPose(Axis.YP.rotationDegrees((entity.tickCount + tickDelta) * 20.0f));
            matrices.translate(-0.5, -0.25, -0.5);
        }
        blockRenderer.renderSingleBlock(Blocks.COBWEB.defaultBlockState(), matrices, vertexConsumers,
                light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        matrices.popPose();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(TrapperNetEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

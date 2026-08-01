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

package io.wifi.starrailexpress.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.wifi.starrailexpress.content.entity.FirecrackerEntity;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

@SuppressWarnings("deprecation")
public class FirecrackerEntityRenderer extends EntityRenderer<FirecrackerEntity> {
    private final ItemRenderer itemRenderer;
    private final float scale;

    public FirecrackerEntityRenderer(EntityRendererProvider.Context ctx, float scale) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.scale = scale;
    }

    public FirecrackerEntityRenderer(EntityRendererProvider.Context context) {
        this(context, 1.0F);
    }

    @Override
    public void render(FirecrackerEntity entity, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        if (entity.tickCount >= 2 || !(this.entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 12.25)) {
            matrices.pushPose();
            matrices.scale(this.scale, this.scale, this.scale);
            matrices.translate(0, entity.hashCode() % 30 / 1000f, 0); // prevent z-fighting
            matrices.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            matrices.mulPose(Axis.XP.rotationDegrees(90));
            this.itemRenderer
                    .renderStatic(
                            TMMItems.FIRECRACKER.getDefaultInstance(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, matrices, vertexConsumers, entity.level(), entity.getId()
                    );
            matrices.popPose();
            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(FirecrackerEntity entity)  {
        return TextureAtlas.LOCATION_BLOCKS;
    }


}

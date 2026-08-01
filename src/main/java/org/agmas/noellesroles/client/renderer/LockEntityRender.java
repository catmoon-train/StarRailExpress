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
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.agmas.noellesroles.content.entity.LockEntity;
import org.agmas.noellesroles.init.ModItems;

public class LockEntityRender extends EntityRenderer<LockEntity> {
    private final ItemRenderer itemRenderer;
    private final float scale;

    public LockEntityRender(EntityRendererProvider.Context ctx, float scale){
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.scale = scale;
    }

    public LockEntityRender(EntityRendererProvider.Context context){
        this(context, 1.0f);
    }

    @Override
    public void render(LockEntity entity, float yaw, float tickDelta, PoseStack poseStack, MultiBufferSource multiBufferSource, int light) {
        if (entity.tickCount >= 2 || !(this.entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 12.25)) {
            poseStack.pushPose();
            poseStack.scale(this.scale, this.scale, this.scale);
            poseStack.translate(0, entity.hashCode() % 30 / 1000f, 0); // prevent z-fighting
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
            this.itemRenderer
                    .renderStatic(
                            ModItems.LOCK_ITEM.getDefaultInstance(), ItemDisplayContext.GROUND, light,
                            OverlayTexture.NO_OVERLAY, poseStack, multiBufferSource, entity.level(), entity.getId()
                    );
            poseStack.popPose();
            super.render(entity, yaw, tickDelta, poseStack, multiBufferSource, light);
        }
    }
    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(LockEntity entity)  {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

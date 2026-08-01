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

import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.NotNull;

public class NoteEntityRenderer extends EntityRenderer<NoteEntity> {
    private final ItemRenderer itemRenderer;
    private final float scale;

    public NoteEntityRenderer(EntityRendererProvider.Context ctx, float scale) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.scale = scale;
    }

    public NoteEntityRenderer(EntityRendererProvider.Context context) {
        this(context, 1.0F);
    }

    @Override
    public void render(@NotNull NoteEntity note, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        if (note.tickCount >= 2 || !(this.entityRenderDispatcher.camera.getEntity().distanceToSqr(note) < 12.25)) {
            matrices.pushPose();
            matrices.translate(0, note.getBbHeight() / 2f, 0);
            matrices.mulPose(note.getDirection().getRotation());
            matrices.mulPose(Axis.YP.rotationDegrees(-note.getYRot()));
            matrices.translate(0, note.hashCode() % 24f * .0001f, 0);
            matrices.mulPose(Axis.XP.rotationDegrees(90));
            matrices.scale(this.scale * .4f, this.scale * .4f, this.scale * .4f);
            this.itemRenderer.renderStatic(TMMItems.NOTE.getDefaultInstance(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, matrices, vertexConsumers, note.level(), note.getId());
            matrices.popPose();
            super.render(note, yaw, tickDelta, matrices, vertexConsumers, light);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(NoteEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
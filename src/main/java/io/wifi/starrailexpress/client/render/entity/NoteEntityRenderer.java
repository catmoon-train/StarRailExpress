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
 * along with this program.  If you did not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class NoteEntityRenderer extends EntityRenderer<NoteEntity> {
    private static final double SPAWN_HIDE_DIST_SQ = 12.25;
    private static final double MAX_RENDER_DISTANCE = 40.0;

    private final ItemRenderer itemRenderer;
    private final float scale;
    private final ItemStack noteStack;
    private final double maxDistSq;

    public NoteEntityRenderer(EntityRendererProvider.Context ctx, float scale) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.scale = scale;
        this.noteStack = TMMItems.NOTE.getDefaultInstance();
        double max = MAX_RENDER_DISTANCE * scale;
        this.maxDistSq = max * max;
    }

    public NoteEntityRenderer(EntityRendererProvider.Context context) {
        this(context, 1.0F);
    }

    @Override
    public boolean shouldRender(NoteEntity note, Frustum frustum, double camX, double camY, double camZ) {
        double dx = note.getX() - camX;
        double dy = note.getY() - camY;
        double dz = note.getZ() - camZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (note.tickCount < 2 && distSq < SPAWN_HIDE_DIST_SQ) {
            return false;
        }
        if (distSq > this.maxDistSq) {
            return false;
        }
        return frustum.isVisible(note.getBoundingBoxForCulling());
    }

    @Override
    public void render(@NotNull NoteEntity note, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {
        matrices.pushPose();
        matrices.translate(0, note.getBbHeight() / 2f, 0);
        matrices.mulPose(note.getDirection().getRotation());
        matrices.mulPose(Axis.YP.rotationDegrees(-note.getYRot()));
        matrices.translate(0, Math.floorMod(note.seed, 24) * .0001f, 0);
        matrices.mulPose(Axis.XP.rotationDegrees(90));
        matrices.scale(this.scale * .4f, this.scale * .4f, this.scale * .4f);
        this.itemRenderer.renderStatic(this.noteStack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                matrices, vertexConsumers, note.level(), note.getId());
        matrices.popPose();
        super.render(note, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(NoteEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}

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

package org.agmas.noellesroles.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.ParrotModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.content.entity.MechanicalBirdEntity;

/** 机械小鸟：原版鹦鹉模型 + 灰色鹦鹉贴图（黑白机械外观）。 */
public class MechanicalBirdRenderer extends EntityRenderer<MechanicalBirdEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/parrot/parrot_grey.png");
    private final ParrotModel model;

    public MechanicalBirdRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new ParrotModel(ctx.bakeLayer(ModelLayers.PARROT));
        this.shadowRadius = 0.25F;
    }

    @Override
    public ResourceLocation getTextureLocation(MechanicalBirdEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(MechanicalBirdEntity entity, float yaw, float partialTicks, PoseStack stack,
            MultiBufferSource buffers, int light) {
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
        stack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        stack.mulPose(Axis.XP.rotationDegrees(-15.0F));
        stack.translate(0.0F, -1.5F, 0.0F);
        stack.scale(1.15F, 1.15F, 1.15F);

        this.model.young = false;
        this.model.attackTime = 0.0F;
        this.model.riding = false;

        var vertexConsumer = buffers.getBuffer(this.model.renderType(TEXTURE));
        this.model.renderToBuffer(stack, vertexConsumer, light, OverlayTexture.NO_OVERLAY);

        stack.popPose();
        super.render(entity, yaw, partialTicks, stack, buffers, light);
    }
}

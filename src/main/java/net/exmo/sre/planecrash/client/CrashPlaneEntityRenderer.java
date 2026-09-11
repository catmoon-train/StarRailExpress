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

package net.exmo.sre.planecrash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.SRE;
import net.exmo.sre.planecrash.CrashPlaneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class CrashPlaneEntityRenderer extends EntityRenderer<CrashPlaneEntity> {
    private static final ResourceLocation TEXTURE = SRE.id("textures/entity/crash_plane.png");
    private static final float MODEL_SCALE = 2.6F;

    private final CrashPlaneEntityModel model;

    public CrashPlaneEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new CrashPlaneEntityModel(context.bakeLayer(CrashPlaneEntityModel.LAYER_LOCATION));
        this.shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(CrashPlaneEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(CrashPlaneEntity entity, float yaw, float tickDelta, PoseStack poseStack,
            MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(tickDelta, entity.yRotO, entity.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(tickDelta, entity.xRotO, entity.getXRot())));
        model.setupAnim(entity, 0.0F, 0.0F, entity.tickCount + tickDelta, 0.0F, 0.0F);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        model.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();
        super.render(entity, yaw, tickDelta, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CrashPlaneEntity entity) {
        return TEXTURE;
    }
}

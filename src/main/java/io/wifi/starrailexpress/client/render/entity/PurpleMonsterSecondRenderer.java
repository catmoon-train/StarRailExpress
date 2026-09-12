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
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.model.entity.PurpleMonsterSecondModel;
import io.wifi.starrailexpress.content.entity.PurpleMonsterSecondEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class PurpleMonsterSecondRenderer extends EntityRenderer<PurpleMonsterSecondEntity> {
    private static final ResourceLocation TEXTURE = SRE.id("textures/entity/purple_monster_second.png");
    private static final ResourceLocation EYES = SRE.id("textures/entity/purple_monster_second_eyes.png");

    private final PurpleMonsterSecondModel model;

    public PurpleMonsterSecondRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new PurpleMonsterSecondModel(context.bakeLayer(PurpleMonsterSecondModel.LAYER_LOCATION));
        this.shadowRadius = 0.55F;
    }

    @Override
    public void render(PurpleMonsterSecondEntity entity, float yaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.5F, 0.0F);

        float limbSwing = entity.walkAnimation.position(partialTicks);
        float limbSwingAmount = entity.walkAnimation.speed(partialTicks);
        float age = entity.tickCount + partialTicks;
        float netHead = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot) - bodyYaw;
        float headPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        this.model.setupAnim(entity, limbSwing, limbSwingAmount, age, netHead, headPitch);

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        VertexConsumer eyes = buffers.getBuffer(RenderType.eyes(EYES));
        this.model.renderToBuffer(poseStack, eyes, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PurpleMonsterSecondEntity entity) {
        return TEXTURE;
    }
}

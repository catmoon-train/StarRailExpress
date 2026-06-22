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
import org.agmas.noellesroles.content.entity.PigeonEntity;

/** 信鸽渲染器：灰色鹦鹉 */
public class PigeonRenderer extends EntityRenderer<PigeonEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/parrot/parrot_grey.png");
    private final ParrotModel model;

    public PigeonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new ParrotModel(ctx.bakeLayer(ModelLayers.PARROT));
    }

    @Override
    public ResourceLocation getTextureLocation(PigeonEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(PigeonEntity entity, float yaw, float partialTicks, PoseStack stack, MultiBufferSource buffers, int light) {
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
        stack.translate(0.0F, -1.2F, 0.0F);
        stack.scale(1.4F, 1.4F, 1.4F);

        this.model.young = false;
        this.model.attackTime = 0.0F;
        this.model.riding = false;

        var vertexConsumer = buffers.getBuffer(this.model.renderType(TEXTURE));
        this.model.renderToBuffer(stack, vertexConsumer, light, OverlayTexture.NO_OVERLAY);

        stack.popPose();
        super.render(entity, yaw, partialTicks, stack, buffers, light);
    }
}

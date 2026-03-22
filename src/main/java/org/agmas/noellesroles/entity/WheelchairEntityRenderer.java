package org.agmas.noellesroles.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public class WheelchairEntityRenderer extends LivingEntityRenderer<WheelchairEntity, WheelchairEntityModel> {
    private static final ResourceLocation TEXTURE = Noellesroles.id("textures/entity/wheelchair.png");

    public WheelchairEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new WheelchairEntityModel(context.bakeLayer(WheelchairEntityModel.LAYER_LOCATION)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(WheelchairEntity entity) {
        return TEXTURE;
    }
}
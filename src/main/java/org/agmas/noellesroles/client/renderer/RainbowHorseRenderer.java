package org.agmas.noellesroles.client.renderer;

import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.RainbowHorseEntity;

/**
 * 海曼彩虹马渲染器：复用原版马模型，纹理换成彩虹皮肤。
 */
public class RainbowHorseRenderer
        extends AbstractHorseRenderer<RainbowHorseEntity, HorseModel<RainbowHorseEntity>> {

    private static final ResourceLocation TEXTURE = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "textures/entity/rainbow_horse.png");

    public RainbowHorseRenderer(EntityRendererProvider.Context context) {
        super(context, new HorseModel<>(context.bakeLayer(ModelLayers.HORSE)), 1.1F);
    }

    @Override
    public ResourceLocation getTextureLocation(RainbowHorseEntity entity) {
        return TEXTURE;
    }
}

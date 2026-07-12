package org.agmas.noellesroles.client.renderer;

import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.CanyuesaHorseEntity;

/**
 * 残月萨马渲染器：复用原版马模型，纹理换成残月萨皮肤。
 */
public class CanyuesaHorseRenderer
        extends AbstractHorseRenderer<CanyuesaHorseEntity, HorseModel<CanyuesaHorseEntity>> {

    private static final ResourceLocation TEXTURE = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "textures/entity/canyuesa_horse.png");

    public CanyuesaHorseRenderer(EntityRendererProvider.Context context) {
        super(context, new HorseModel<>(context.bakeLayer(ModelLayers.HORSE)), 1.1F);
    }

    @Override
    public ResourceLocation getTextureLocation(CanyuesaHorseEntity entity) {
        return TEXTURE;
    }
}

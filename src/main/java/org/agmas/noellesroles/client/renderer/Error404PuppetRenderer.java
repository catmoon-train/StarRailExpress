package org.agmas.noellesroles.client.renderer;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.Error404PuppetEntity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** 独立错误傀儡使用固定黑紫玩家皮肤，不再覆盖真人玩家皮肤。 */
@Environment(EnvType.CLIENT)
public final class Error404PuppetRenderer
        extends HumanoidMobRenderer<Error404PuppetEntity, PlayerModel<Error404PuppetEntity>> {
    private static final ResourceLocation TEXTURE = Noellesroles.id("textures/entity/player/error404_puppet.png");

    public Error404PuppetRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.4F);
    }

    @Override public ResourceLocation getTextureLocation(Error404PuppetEntity entity) { return TEXTURE; }
}

package net.exmo.sre.sixtyseconds.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.exmo.sre.sixtyseconds.entity.OceanSeaMonsterEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 海怪渲染器：Scale 属性处理大体积，走 {@link MobRenderer} 的标准缩放管线。
 */
public class OceanSeaMonsterRenderer extends MobRenderer<OceanSeaMonsterEntity, OceanSeaMonsterModel> {

    public OceanSeaMonsterRenderer(EntityRendererProvider.Context context) {
        super(context, new OceanSeaMonsterModel(OceanSeaMonsterModel.create().bakeRoot()), 1.2F);
    }

    @Override
    public void render(OceanSeaMonsterEntity entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(OceanSeaMonsterEntity entity) {
        return entity.textureLocation();
    }
}

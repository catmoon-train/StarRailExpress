package net.exmo.sre.sixtyseconds.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/** 房车渲染器：自研盒子模型，整体放大到载具尺寸。 */
public class SixtySecondsRvRenderer
        extends LivingEntityRenderer<SixtySecondsRvEntity, SixtySecondsRvModel> {

    private static final ResourceLocation TEXTURE =
            Noellesroles.id("textures/entity/sixty_seconds_rv.png");
    private static final float MODEL_SCALE = 3.2F;

    public SixtySecondsRvRenderer(EntityRendererProvider.Context context) {
        super(context, new SixtySecondsRvModel(SixtySecondsRvModel.createRv().bakeRoot()), 3.0F);
    }

    @Override
    protected void scale(SixtySecondsRvEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(SixtySecondsRvEntity entity) {
        return TEXTURE;
    }

    @Override
    protected boolean shouldShowName(SixtySecondsRvEntity entity) {
        return false;
    }
}

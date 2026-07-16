package net.exmo.sre.sixtyseconds.client.render;

import net.exmo.sre.sixtyseconds.entity.SixtySecondsArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 60s 箭矢渲染器：复用原版箭矢外观（不额外做贴图）。
 */
public class SixtySecondsArrowRenderer extends ArrowRenderer<SixtySecondsArrowEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");

    public SixtySecondsArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SixtySecondsArrowEntity entity) {
        return TEXTURE;
    }
}

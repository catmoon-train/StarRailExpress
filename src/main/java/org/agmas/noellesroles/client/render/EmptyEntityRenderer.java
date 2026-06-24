package org.agmas.noellesroles.client.render;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.Noellesroles;

public class EmptyEntityRenderer<T extends Entity> extends EntityRenderer<T> {
    private static final ResourceLocation TEXTURE = Noellesroles.id("textures/entity/empty.png");

    public EmptyEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TEXTURE;
    }
}

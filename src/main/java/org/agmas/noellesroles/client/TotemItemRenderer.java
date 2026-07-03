package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ItemSupplier;

public class TotemItemRenderer<T extends Entity & ItemSupplier> extends ThrownItemRenderer<T > {
    public TotemItemRenderer(EntityRendererProvider.Context context, float f, boolean bl) {
        super(context, f, bl);
    }

    public TotemItemRenderer(EntityRendererProvider.Context context) {

        super(context);
    }

    @Override
    public void render(T entity, float f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, int i) {
        Minecraft instance = Minecraft.getInstance();
        if (instance.player != null){
            if (!SREClient.isKiller()) {
                return;
            }
        }
        super.render(entity, f, g, poseStack, multiBufferSource, i);
    }
}

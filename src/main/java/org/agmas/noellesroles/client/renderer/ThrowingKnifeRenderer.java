package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.jspecify.annotations.Nullable;

public class ThrowingKnifeRenderer extends ArrowRenderer {
    public ThrowingKnifeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @Nullable ResourceLocation getTextureLocation(Entity entity) {
        return ResourceLocation.tryParse("noellesroles:textures/entity/throwing_knife.png");
    }

    @Override
    public void render(AbstractArrow entity, float f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, int i) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player!=null){
            if (player.hasEffect(ModEffects.TIME_STOP)){
                if (!TimeStopEffect.canMovePlayers.contains(player.getUUID()))return;
            }
        }

        super.render(entity, f, g, poseStack, multiBufferSource, i);
    }
}

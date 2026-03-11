package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.entity.LockEntity;

public class LockEntityRender extends EntityRenderer<LockEntity> {
    private final ItemRenderer itemRenderer;
    private final float scale;

    public LockEntityRender(EntityRendererProvider.Context ctx, float scale){
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.scale = scale;
    }

    public LockEntityRender(EntityRendererProvider.Context context){
        this(context, 1.0f);
    }

    @Override
    public void render(LockEntity entity, float yaw, float tickDelta, PoseStack poseStack, MultiBufferSource multiBufferSource, int light) {
        if (entity.tickCount >= 2 || !(this.entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 12.25)) {
            poseStack.pushPose();
            poseStack.scale(this.scale, this.scale, this.scale);
            poseStack.translate(0, entity.hashCode() % 30 / 1000f, 0); // prevent z-fighting
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
            this.itemRenderer
                    .renderStatic(
                            ModItems.LOCK_ITEM.getDefaultInstance(), ItemDisplayContext.GROUND, light,
                            OverlayTexture.NO_OVERLAY, poseStack, multiBufferSource, entity.level(), entity.getId()
                    );
            poseStack.popPose();
            super.render(entity, yaw, tickDelta, poseStack, multiBufferSource, light);
        }
    }
    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(LockEntity entity)  {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

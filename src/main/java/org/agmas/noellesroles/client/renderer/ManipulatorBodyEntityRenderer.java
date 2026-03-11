package org.agmas.noellesroles.client.renderer;

import org.agmas.noellesroles.entity.ManipulatorBodyEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * 操纵师本体实体渲染器
 * 
 */
public class ManipulatorBodyEntityRenderer extends EntityRenderer<ManipulatorBodyEntity> {

    private final HumanoidModel<ManipulatorBodyEntity> model;

    public ManipulatorBodyEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER));
    }

    @Override
    public void render(ManipulatorBodyEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {

        ResourceLocation texture = getTextureLocation(entity);

        RenderType renderLayer = RenderType.entityTranslucent(texture);
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(renderLayer);

        model.setupAnim(entity, 0, 0, entity.tickCount + tickDelta, 0, 0);

        model.renderToBuffer(matrices, vertexConsumer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ManipulatorBodyEntity entity) {
        UUID ownerUuid = entity.getOwnerUuid().orElse(null);

        if (ownerUuid != null) {
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() != null) {
                PlayerInfo entry = client.getConnection().getPlayerInfo(ownerUuid);
                if (entry != null) {
                    return entry.getSkin().texture();
                }
            }
            return DefaultPlayerSkin.get(ownerUuid).texture();
        }

        GameProfile profile = entity.getSkinProfile();
        if (profile != null && profile.getId() != null) {
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() != null) {
                PlayerInfo entry = client.getConnection().getPlayerInfo(profile.getId());
                if (entry != null) {
                    return entry.getSkin().texture();
                }
            }
            return DefaultPlayerSkin.get(profile.getId()).texture();
        }

        return DefaultPlayerSkin.get(new UUID(0, 0)).texture();
    }
}
package org.agmas.noellesroles.client.renderer;

import org.agmas.noellesroles.entity.KuiXiPuppetEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.wifi.starrailexpress.client.SREClient;

import java.util.UUID;
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
public class KuiXiBodyEntityRenderer extends EntityRenderer<KuiXiPuppetEntity> {
    private final HumanoidModel<KuiXiPuppetEntity> model;

    public KuiXiBodyEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER));
    }

    @Override
    public void render(KuiXiPuppetEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {
        ResourceLocation texture = getTextureLocation(entity);
        RenderType renderLayer = RenderType.entityTranslucent(texture);
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(renderLayer);

        model.setupAnim(entity, 0, 0, entity.tickCount + tickDelta, 0, 0);

        model.renderToBuffer(matrices, vertexConsumer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(KuiXiPuppetEntity entity) {
        // 首先尝试通过 ownerUuid 从玩家列表获取皮肤
        UUID ownerUuid = entity.getOwnerUuid();

        if (ownerUuid != null) {
            // 通过 UUID 从玩家列表获取皮肤
            PlayerInfo entry = SREClient.PLAYER_ENTRIES_CACHE.get(ownerUuid);
            if (entry != null) {
                return entry.getSkin().texture();
            }
            // 如果玩家不在列表中（可能离线），使用基于 UUID 的默认皮肤
            return DefaultPlayerSkin.get(ownerUuid).texture();
        }
        // 最后的回退：使用固定的默认皮肤（Steve）
        return DefaultPlayerSkin.get(UUID.fromString("7833c811-436e-40c4-868a-ffb1073f48a2")).texture();
    }
}
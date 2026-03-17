package org.agmas.noellesroles.client.renderer;

import org.agmas.noellesroles.entity.KuiXiPuppetEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;

import io.wifi.starrailexpress.client.SREClient;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * 操纵师本体实体渲染器
 * 
 */
public class KuiXiBodyEntityRenderer extends EntityRenderer<KuiXiPuppetEntity> {

    public KuiXiBodyEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);

    }

    @Override
    public void render(KuiXiPuppetEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {

        // matrices.pushPose();

        // 调整渲染位置和旋转
        // matrices.translate(0.0, 0.0, 0.0);

        // 获取玩家皮肤纹理
        // ResourceLocation texture = getTextureLocation(entity);
        final var instance = Minecraft.getInstance();
        UUID ownerUuid = entity.getOwnerUuid();

        PlayerInfo entry = SREClient.PLAYER_ENTRIES_CACHE.get(ownerUuid);
        if (entry != null) {
            AbstractClientPlayer fakePlayer = new RemotePlayer(instance.level,
                    new GameProfile(ownerUuid, entry.getProfile().getName()));
            Minecraft.getInstance().getEntityRenderDispatcher().render(fakePlayer, 0.0D, 0.0D, 0, 0, 0, matrices,
                    vertexConsumers, light);

        } else {
            AbstractClientPlayer fakePlayer = new RemotePlayer(instance.level,
                    new GameProfile(UUID.randomUUID(), "pupu"));
            Minecraft.getInstance().getEntityRenderDispatcher().render(fakePlayer, 0.0D, 0.0D, 0, 0, 0, matrices,
                    vertexConsumers, light);

        }

        // matrices.popPose();

        // super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
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
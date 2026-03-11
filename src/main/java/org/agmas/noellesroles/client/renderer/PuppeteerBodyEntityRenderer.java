package org.agmas.noellesroles.client.renderer;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import org.agmas.noellesroles.entity.PuppeteerBodyEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * 傀儡本体实体渲染器
 * 
 * 使用玩家皮肤渲染傀儡本体
 */
public class PuppeteerBodyEntityRenderer extends EntityRenderer<PuppeteerBodyEntity> {
    

    
    public PuppeteerBodyEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);

    }
    
    @Override
    public void render(PuppeteerBodyEntity entity, float yaw, float tickDelta, PoseStack matrices, 
            MultiBufferSource vertexConsumers, int light) {
        
        //matrices.pushPose();
        
        // 调整渲染位置和旋转
        //matrices.translate(0.0, 0.0, 0.0);
        
        // 获取玩家皮肤纹理
//        ResourceLocation texture = getTextureLocation(entity);
        final var instance = Minecraft.getInstance();
        UUID ownerUuid = entity.getOwnerUuid().orElse(null);
        PlayerInfo entry = instance.getConnection().getPlayerInfo(ownerUuid);
        if (entry != null) {
            AbstractClientPlayer fakePlayer = new RemotePlayer(instance.level, new GameProfile(ownerUuid, entry.getProfile().getName()));
            Minecraft.getInstance().getEntityRenderDispatcher().render(fakePlayer, 0.0D, 0.0D, 0, 0, 0, matrices, vertexConsumers, light);

        }else {
            AbstractClientPlayer fakePlayer = new RemotePlayer(instance.level, new GameProfile(UUID.randomUUID(), "pupu"));
            Minecraft.getInstance().getEntityRenderDispatcher().render(fakePlayer, 0.0D, 0.0D, 0, 0, 0, matrices, vertexConsumers, light);

        }
        
        //matrices.popPose();
        
//        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }


    @Override
    public ResourceLocation getTextureLocation(PuppeteerBodyEntity entity) {
        // 首先尝试通过 ownerUuid 从玩家列表获取皮肤
        UUID ownerUuid = entity.getOwnerUuid().orElse(null);
        
        if (ownerUuid != null) {
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() != null) {
                // 通过 UUID 从玩家列表获取皮肤
                PlayerInfo entry = client.getConnection().getPlayerInfo(ownerUuid);
                if (entry != null) {
                    return entry.getSkin().texture();
                }
            }
            // 如果玩家不在列表中（可能离线），使用基于 UUID 的默认皮肤
            return DefaultPlayerSkin.get(ownerUuid).texture();
        }
        
        // 尝试从 GameProfile 获取（服务端设置的备选方案）
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
        
        // 最后的回退：使用固定的默认皮肤（Steve）
        return DefaultPlayerSkin.get(new UUID(0, 0)).texture();
    }
}
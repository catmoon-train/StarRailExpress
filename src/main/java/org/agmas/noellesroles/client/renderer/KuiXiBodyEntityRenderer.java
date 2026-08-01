/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client.renderer;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.content.entity.KuiXiPuppetEntity;

import java.util.UUID;

/**
 * KuiXiPuppetEntity 渲染玩家
 */
public class KuiXiBodyEntityRenderer extends EntityRenderer<KuiXiPuppetEntity> {

    public KuiXiBodyEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(KuiXiPuppetEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {
        final var instance = Minecraft.getInstance();
        UUID ownerUuid = entity.getOwnerUuid();
        PlayerInfo entry = ClientSkinCache.getCachedPlayerInfo(ownerUuid);
        if (entry != null) {
            AbstractClientPlayer fakePlayer = new RemotePlayer(instance.level,
                    new GameProfile(ownerUuid, entry.getProfile().getName()));
            Minecraft.getInstance().getEntityRenderDispatcher().render(fakePlayer, 0.0D, 0.0D, 0, 0, 0, matrices,
                    vertexConsumers, light);

        } else {
            super.render(entity, tickDelta, light, matrices, vertexConsumers, light);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(KuiXiPuppetEntity entity) {
        // 首先尝试通过 ownerUuid 从玩家列表获取皮肤
        UUID ownerUuid = entity.getOwnerUuid();

        if (ownerUuid != null) {
            // 通过 UUID 从玩家列表获取皮肤
            PlayerInfo entry = ClientSkinCache.getCachedPlayerInfo(ownerUuid);
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
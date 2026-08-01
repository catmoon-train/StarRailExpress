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
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.entity.MorphlingKnifeDummyEntity;

import java.util.UUID;

/**
 * 举刀假人渲染器：复用变形者当前样貌（伪装对象或自身）渲染为手持匕首的玩家外观。
 */
public class MorphlingKnifeDummyRenderer extends EntityRenderer<MorphlingKnifeDummyEntity> {

    public MorphlingKnifeDummyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(MorphlingKnifeDummyEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {
        final var instance = Minecraft.getInstance();
        UUID skinUuid = entity.getSkinUuid();
        if (skinUuid != null && instance.level != null) {
            PlayerInfo entry = ClientSkinCache.getCachedPlayerInfo(skinUuid);
            String name = entry != null ? entry.getProfile().getName() : "Dummy";
            AbstractClientPlayer fakePlayer = new RemotePlayer(instance.level,
                    new GameProfile(skinUuid, name));
            // 朝向跟随假人移动方向
            fakePlayer.setYRot(entity.getYRot());
            fakePlayer.yRotO = entity.yRotO;
            fakePlayer.setYBodyRot(entity.yBodyRot);
            fakePlayer.yBodyRotO = entity.yBodyRotO;
            fakePlayer.setYHeadRot(entity.getYHeadRot());
            fakePlayer.yHeadRotO = entity.yHeadRotO;
            fakePlayer.setXRot(entity.getXRot());
            fakePlayer.xRotO = entity.xRotO;
            // 同步行走动画状态：复制假人实体自身的 limbSwing，使其迈腿而非平滑滑行
            fakePlayer.walkAnimation.speed = entity.walkAnimation.speed;
            fakePlayer.walkAnimation.speedOld = entity.walkAnimation.speedOld;
            fakePlayer.walkAnimation.position = entity.walkAnimation.position;
            // 举刀：主手持匕首并摆出举刀姿态
            fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TMMItems.KNIFE));
            fakePlayer.startUsingItem(InteractionHand.MAIN_HAND);
            instance.getEntityRenderDispatcher().render(fakePlayer, 0.0D, 0.0D, 0.0D, 0, tickDelta, matrices,
                    vertexConsumers, light);
            return;
        }
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MorphlingKnifeDummyEntity entity) {
        UUID skinUuid = entity.getSkinUuid();
        if (skinUuid != null) {
            PlayerInfo entry = ClientSkinCache.getCachedPlayerInfo(skinUuid);
            if (entry != null) {
                return entry.getSkin().texture();
            }
            return DefaultPlayerSkin.get(skinUuid).texture();
        }
        return DefaultPlayerSkin.get(UUID.fromString("7833c811-436e-40c4-868a-ffb1073f48a2")).texture();
    }
}

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

package org.agmas.noellesroles.mixin.client.roles.insane;

import com.mojang.blaze3d.vertex.PoseStack;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.index.TMMEntities;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.role_data.killer.InsaneKillerRoleData;
import org.agmas.noellesroles.utils.BodyCleanupZones;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.agmas.noellesroles.role_data.killer.InsaneKillerRoleData.isPlayerBodyEntity;
import static org.agmas.noellesroles.role_data.killer.InsaneKillerRoleData.playerBodyEntities;

@Mixin(PlayerRenderer.class)
public abstract class InsaneKillerRenderMixin
        extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public InsaneKillerRenderMixin(EntityRendererProvider.Context context,
            PlayerModel<AbstractClientPlayer> entityModel, float f) {
        super(context, entityModel, f);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    protected void setupRotations(AbstractClientPlayer abstractClientPlayer, float f, float g, PoseStack poseStack,
            MultiBufferSource multiBufferSource, int i, CallbackInfo ci) {
        if (abstractClientPlayer.isSpectator())
            return;
        InsaneKillerRoleData component = RoleData.getNullable(InsaneKillerRoleData.class, abstractClientPlayer);
        ClientLevel clientLevel = abstractClientPlayer.clientLevel;
        if (component != null && component.isActive) {
            ci.cancel();
            isPlayerBodyEntity.put(abstractClientPlayer.getUUID(), true);
            // 会议区/游记放逐区会自动清除尸体，进去就再也回不来了：
            // 本体在区域内时不动假尸体（既不新建也不跟随），离开后再恢复。
            if (!BodyCleanupZones.isInside(abstractClientPlayer)) {
                PlayerBodyEntity cached = playerBodyEntities.get(abstractClientPlayer.getUUID());
                if (cached != null && cached.isRemoved()) {
                    // 尸体已被清理，丢掉失效引用重建，否则伪装永远不再出现
                    playerBodyEntities.remove(abstractClientPlayer.getUUID());
                    cached = null;
                }
                if (cached == null) {
                    // if (abstractClientPlayer.getUUID() ==
                    // Minecraft.getInstance().player.getUUID()){
                    // Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    // }
                    final var body = new PlayerBodyEntity(TMMEntities.PLAYER_BODY, abstractClientPlayer.level());
                    body.setPlayerUuid(abstractClientPlayer.getUUID());
                    // 标记为「伪造的尸体」：验尸官能察觉这是亡语杀手假扮的
                    body.getComponent().isFakeBody = true;
                    Vec3 spawnPos = abstractClientPlayer.position()
                            .add(abstractClientPlayer.getLookAngle().normalize().scale(1));
                    body.moveTo(spawnPos.x(), abstractClientPlayer.getY(), spawnPos.z(), 0, 0f);
                    // body.setYRot(abstractClientPlayer.getYHeadRot());
                    // body.setYHeadRot(abstractClientPlayer.getYHeadRot());
                    clientLevel.addEntity(body);
                    playerBodyEntities.put(abstractClientPlayer.getUUID(), body);
                    cached = body;
                }

                Entity entity = clientLevel.getEntity(cached.getId());
                if (entity != null) {
                    entity.setPos(abstractClientPlayer.getX(), abstractClientPlayer.getY(),
                            abstractClientPlayer.getZ());
                    entity.setYHeadRot(0);
                    // entity.setXRot(0);
                }
            }
            // playerBodyEntity.moveTo(abstractClientPlayer.getX(),
            // abstractClientPlayer.getY(), abstractClientPlayer.getZ());
            // Minecraft.getInstance().getEntityRenderDispatcher().render(playerBodyEntity,
            // 0.0D, 0.0D, 0, f, g, poseStack, multiBufferSource, i);
            // 模拟尸体渲染：绕 Z 轴旋转 90 度，并下移到地面
            // poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            // poseStack.translate(0.0F, -0.85F, 0.0F);
        } else {
            if (isPlayerBodyEntity.getOrDefault(abstractClientPlayer.getUUID(), false)) {
                // if (abstractClientPlayer == Minecraft.getInstance().player) {
                // Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
                // }
                isPlayerBodyEntity.put(abstractClientPlayer.getUUID(), false);
                if (playerBodyEntities.containsKey(abstractClientPlayer.getUUID())) {
                    clientLevel.removeEntity(playerBodyEntities.get(abstractClientPlayer.getUUID()).getId(),
                            Entity.RemovalReason.DISCARDED);
                    playerBodyEntities.remove(abstractClientPlayer.getUUID());

                }
            }
        }
    }
}
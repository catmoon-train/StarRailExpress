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
import io.wifi.starrailexpress.index.TMMEntities;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.agmas.noellesroles.client.PlayerBodyDisguiseRenderer;
import org.agmas.noellesroles.role_data.killer.InsaneKillerRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        if (component == null)
            return;
        if (component.isActive) {
            // 伪装尸体改为纯渲染：不入世界的客户端尸体不会被会议区/游记放逐区清理掉，
            // 进塔罗会、用游记传送后回来都照常显示。
            // 标记为「伪造的尸体」：验尸官能察觉这是亡语杀手假扮的。
            if (PlayerBodyDisguiseRenderer.render(abstractClientPlayer, TMMEntities.PLAYER_BODY, f, 0.0F, 0.0F, g,
                    poseStack, multiBufferSource, i, true)) {
                ci.cancel();
            }
        } else {
            // 伪装结束：丢弃缓存的客户端尸体（实体从未入世界，无需 removeEntity）
            PlayerBodyDisguiseRenderer.discard(abstractClientPlayer.getUUID());
        }
    }
}

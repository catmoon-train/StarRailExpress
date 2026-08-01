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

package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerRenderer.class)
public class PlayerEntityRendererMixin {
    @WrapMethod(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V")
    protected void tmm$disableNameTags(AbstractClientPlayer abstractClientPlayerEntity, Component text,
            PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i, float f, Operation<Void> original) {
        if (SREClient.isInLobby) {
            original.call(abstractClientPlayerEntity, text, matrixStack, vertexConsumerProvider, i, f);
        }
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    private void tmm$psychoSkinTexture(
            AbstractClientPlayer abstractClientPlayerEntity, CallbackInfoReturnable<ResourceLocation> cir) {
        if (SREClient.gameComponent == null)
            return;

        // 获取普通状态下职业皮肤
        SRERole role = SREClient.gameComponent.getRole(abstractClientPlayerEntity.getUUID());
        PlayerSkin.Model model = (abstractClientPlayerEntity.getSkin()).model();
        boolean isSLIM = (model == PlayerSkin.Model.SLIM);
        if (role != null) {
            ResourceLocation rolenormalskinresult = role.getNormalSkin(abstractClientPlayerEntity, isSLIM);
            if (rolenormalskinresult != null) {
                cir.setReturnValue(rolenormalskinresult);
                cir.cancel();
                return;
            }
        }
        // 获取疯魔状态下职业皮肤
        if (SREClient.PLAYER_PSYCHO_CACHE.getOrDefault(abstractClientPlayerEntity.getUUID(), false)) {
            String suffix = isSLIM ? "_thin" : "";
            ResourceLocation texture = SRE.watheId("textures/entity/psycho" + suffix + ".png");
            if (role != null) {
                var res = role.getPsychoSkin(abstractClientPlayerEntity, isSLIM);
                if (res != null) {
                    texture = res;
                }
            }
            cir.setReturnValue(texture);
            cir.cancel();
        }
    }

    @ModifyVariable(method = "renderHand", at = @At("STORE"), ordinal = 0)
    private ResourceLocation tmm$psychoArmTexture(ResourceLocation skinTexture) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && SREClient.localPlayerPsychoActive) {
            if (SREClient.gameComponent == null)
                return skinTexture;
            // 获取普通状态下职业皮肤
            SRERole role = SREClient.gameComponent.getRole(client.player.getUUID());
            PlayerSkin.Model model = client.player.getSkin().model();
            boolean isSLIM = (model == PlayerSkin.Model.SLIM);
            if (role != null) {
                ResourceLocation rolenormalskinresult = role.getNormalSkin(client.player, isSLIM);
                if (rolenormalskinresult != null) {
                    return (rolenormalskinresult);
                }
            }
            // 获取疯魔状态下职业皮肤
            if (SREClient.PLAYER_PSYCHO_CACHE.getOrDefault(client.player.getUUID(), false)) {
                String suffix = isSLIM ? "_thin" : "";
                ResourceLocation texture = SRE.watheId("textures/entity/psycho" + suffix + ".png");
                if (role != null) {
                    var res = role.getPsychoSkin(client.player, isSLIM);
                    if (res != null) {
                        texture = res;
                    }
                }
                return (texture);
            }
        }
        return skinTexture;
    }
}

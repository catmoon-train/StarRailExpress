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

package io.wifi.starrailexpress.mixin.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import io.wifi.starrailexpress.client.disguise.EntityDisguiseRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 伪装中的玩家不再走玩家渲染器：本帧交给 {@link EntityDisguiseRenderer} 用目标实体的渲染器绘制。
 * <p>
 * 取消整个 {@code render} 而不是只换皮肤，所以名牌、帽子、身份玩偶、手持物这些附属渲染
 * 也会一起消失，不会出现「一头牛顶着玩家名牌」。
 * <p>
 * 另外接住第一人称的两条手臂：原版的 {@code ItemInHandRenderer.renderPlayerArm} 就是把手臂绘制
 * 委托给这两个 public 方法的，所以在这里换成目标实体的手臂即可，位姿由原版保证。
 */
@Mixin(PlayerRenderer.class)
public abstract class DisguisedPlayerRenderMixin {

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void sre$renderEntityDisguise(AbstractClientPlayer player, float yaw, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (EntityDisguiseRenderer.render(player, yaw, tickDelta, poseStack, bufferSource, packedLight)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V", at = @At("HEAD"), cancellable = true)
    private void sre$renderDisguisedRightHand(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            AbstractClientPlayer player, CallbackInfo ci) {
        if (EntityDisguiseRenderer.renderFirstPersonHand(player, true, poseStack, bufferSource, packedLight)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V", at = @At("HEAD"), cancellable = true)
    private void sre$renderDisguisedLeftHand(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            AbstractClientPlayer player, CallbackInfo ci) {
        if (EntityDisguiseRenderer.renderFirstPersonHand(player, false, poseStack, bufferSource, packedLight)) {
            ci.cancel();
        }
    }
}

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

package org.agmas.noellesroles.mixin.client.general;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.agmas.noellesroles.content.effects.FearEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 害怕玩家画完后立刻清掉共享模型上的倾斜，避免下一个玩家没来得及 setupAnim 就带着歪身子。
 */
@Mixin(PlayerRenderer.class)
public abstract class FearPlayerRenderCleanupMixin {

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void noellesroles$clearFearPose(AbstractClientPlayer player, float yaw, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (!FearEffects.isActive(player)) {
            return;
        }
        PlayerRenderer renderer = (PlayerRenderer) (Object) this;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        model.body.zRot = 0.0f;
        model.head.zRot = 0.0f;
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
        model.riding = player.isPassenger();
    }
}

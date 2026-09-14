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

package org.agmas.noellesroles.mixin.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

import org.agmas.noellesroles.client.AllayDisguiseRenderer;
import org.agmas.noellesroles.client.LeatherPigDisguiseRenderer;
import org.agmas.noellesroles.client.PandaDisguiseRenderer;
import org.agmas.noellesroles.client.RabbitDisguiseRenderer;
import org.agmas.noellesroles.client.RoleDisguiseResolver;
import org.agmas.noellesroles.client.TomatoHeadDisguiseRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 职业形态伪装：把玩家画成猪 / 兔 / 番茄头 / 悦灵 / 熊猫。
 * <p>
 * 判定交给 {@link RoleDisguiseResolver}——按 tick 打戳，所以这里每帧只是一次查表加几次布尔读，
 * 不再每帧读墙钟、也不再有最多 200ms 的形态滞后。分支顺序与取消逻辑与原实现逐条一致。
 */
@Mixin(PlayerRenderer.class)
public abstract class EntityDisguisePlayerRenderMixin {

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void noellesroles$renderLeatherPigAsPig(AbstractClientPlayer player, float yaw, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        RoleDisguiseResolver.Flags flags = RoleDisguiseResolver.resolve(player);

        if (flags.panda) {
            if (PandaDisguiseRenderer.render(player, yaw, tickDelta, poseStack, bufferSource, packedLight)) {
                ci.cancel();
            }
            return;
        }

        if (flags.pig) {
            if (LeatherPigDisguiseRenderer.render(player, yaw, tickDelta, poseStack, bufferSource, packedLight)) {
                ci.cancel();
            }
            return;
        }

        if (flags.rabbit) {
            if (RabbitDisguiseRenderer.render(player, yaw, tickDelta, poseStack, bufferSource, packedLight)) {
                ci.cancel();
            }
            return;
        }
        if (flags.tomato) {
            if (TomatoHeadDisguiseRenderer.render(player, yaw, tickDelta, poseStack, bufferSource, packedLight)) {
                ci.cancel();
                return;
            }
        }
        if (flags.allay) {
            if (AllayDisguiseRenderer.render(player, yaw, tickDelta, poseStack, bufferSource, packedLight)) {
                ci.cancel();
            }
        }
    }
}

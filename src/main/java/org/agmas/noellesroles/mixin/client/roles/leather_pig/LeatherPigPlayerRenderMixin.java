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

package org.agmas.noellesroles.mixin.client.roles.leather_pig;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

import org.agmas.noellesroles.client.LeatherPigDisguiseRenderer;
import org.agmas.noellesroles.client.RabbitDisguiseRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class LeatherPigPlayerRenderMixin {
    @Unique
    private long lastCacheTime = 0;
    @Unique
    private boolean cacheResult1 = false;
    private boolean cacheResult2 = false;
    private static final int CACHE_TIME_GAP_EXTREMELY = 200;

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void noellesroles$renderLeatherPigAsPig(AbstractClientPlayer player, float yaw, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        long now = System.currentTimeMillis();
        if (now - lastCacheTime > CACHE_TIME_GAP_EXTREMELY) {
            lastCacheTime = now;
            cacheResult1 = LeatherPigDisguiseRenderer.shouldDisguise(player);
            cacheResult2 = RabbitDisguiseRenderer.shouldDisguise(player);
        }
        if (cacheResult1) {
            if (LeatherPigDisguiseRenderer.render(player, yaw, tickDelta, poseStack, bufferSource, packedLight)) {
                ci.cancel();
            }
            return;
        }

        if (cacheResult2) {
            if (RabbitDisguiseRenderer.render(player, yaw, tickDelta, poseStack, bufferSource, packedLight)) {
                ci.cancel();
            }
            return;
        }
    }
}

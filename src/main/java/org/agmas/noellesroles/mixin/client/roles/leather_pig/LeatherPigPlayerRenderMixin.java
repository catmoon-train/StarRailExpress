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

import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.client.util.ClientSkinCache.CachedDisguiseState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

import java.util.UUID;

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
    private static final int CACHE_TIME_GAP_EXTREMELY = 200;

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void noellesroles$renderLeatherPigAsPig(AbstractClientPlayer player, float yaw, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();

        // 获取或创建该玩家的缓存条目
        CachedDisguiseState state = ClientSkinCache.DISGUISE_CACHE.computeIfAbsent(
                playerId,
                id -> new CachedDisguiseState(false, false, 0));

        // 缓存过期则重新计算
        if (now - state.lastCheckTime > CACHE_TIME_GAP_EXTREMELY) {
            boolean pig = LeatherPigDisguiseRenderer.shouldDisguise(player);
            boolean rabbit = RabbitDisguiseRenderer.shouldDisguise(player);
            state.pig = pig;
            state.rabbit = rabbit;
            state.lastCheckTime = now;
        }

        // 执行伪装渲染
        if (state.pig) {
            if (LeatherPigDisguiseRenderer.render(player, yaw, tickDelta, poseStack, bufferSource, packedLight)) {
                ci.cancel();
            }
            return;
        }

        if (state.rabbit) {
            if (RabbitDisguiseRenderer.render(player, yaw, tickDelta, poseStack, bufferSource, packedLight)) {
                ci.cancel();
            }
            return;
        }
    }
}

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

package org.agmas.noellesroles.mixin.roles.puppeteer;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 允许刀攻击傀儡本体实体
 */
@Mixin(KnifeStabPayload.Receiver.class)
public class PuppeteerBodyKnifePayloadMixin {
    
    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private void handlePuppeteerBodyTarget(KnifeStabPayload payload, ServerPlayNetworking.Context context, CallbackInfo ci) {
        ServerPlayer player = context.player();
        
        // 检查目标是否是傀儡本体实体
        if (player.serverLevel().getEntity(payload.target()) instanceof PuppeteerBodyEntity bodyEntity) {
            if (bodyEntity.distanceTo(player) > 4.0) return;
            
            // 对傀儡本体造成致命伤害（20点以上确保击杀）
            bodyEntity.playerHurt(player, GameConstants.DeathReasons.PUPPETEER_KNIFE);
            
            bodyEntity.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
            player.swing(InteractionHand.MAIN_HAND);
            
            if (!player.isCreative()) {
                player.getCooldowns().addCooldown(TMMItems.KNIFE, GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.KNIFE, 600));
            }
            
            ci.cancel();
        }
    }
}

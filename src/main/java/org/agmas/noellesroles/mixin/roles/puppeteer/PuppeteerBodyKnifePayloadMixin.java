package org.agmas.noellesroles.mixin.roles.puppeteer;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.agmas.noellesroles.entity.PuppeteerBodyEntity;
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
            bodyEntity.hurt(player.damageSources().playerAttack(player), 100.0f);
            
            bodyEntity.playSound(TMMSounds.ITEM_KNIFE_STAB, 1.0f, 1.0f);
            player.swing(InteractionHand.MAIN_HAND);
            
            if (!player.isCreative()) {
                player.getCooldowns().addCooldown(TMMItems.KNIFE, GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.KNIFE, 600));
            }
            
            ci.cancel();
        }
    }
}
package org.agmas.noellesroles.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

@Mixin(ThrownTrident.class)
public class TridentMixin {
    private ServerPlayer lastHitPlayer = null;

    @Inject(method = "onHitEntity", at = @At("TAIL"))
    private void noellesroles$onHitEntity(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (SRE.isLobby)
            return;

        if (entityHitResult.getEntity() instanceof ServerPlayer player) {
            ThrownTrident trident = (ThrownTrident) (Object) this;
            if (trident.getOwner() instanceof ServerPlayer serverPlayer) {
                // 检查是否已经击中过这个玩家（避免重复击杀）
                if (lastHitPlayer == player) {
                    return;
                }

                // 让三叉戟按原版逻辑先造成伤害（通过不取消，让原生逻辑执行）
                // 然后在原生逻辑执行完后，让玩家死亡
                lastHitPlayer = player;
                if (trident.getOwner() instanceof ServerPlayer killer) {
                    if (!killer.isSpectator()) {
                        GameUtils.killPlayer(player, true, serverPlayer, SRE.id("trident"));
                    }
                    killer.getCooldowns().addCooldown(Items.TRIDENT,
                            GameConstants.ITEM_COOLDOWNS.getOrDefault(Items.TRIDENT,
                                    GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.KNIFE, 0)));
                }
                trident.discard();
            }
        }
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void noellesroles$onHitPlayerBody(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if (entityHitResult.getEntity() instanceof PlayerBodyEntity) {
            ThrownTrident trident = (ThrownTrident) (Object) this;
            if (trident.getOwner() instanceof ServerPlayer killer) {
                killer.getCooldowns().addCooldown(Items.TRIDENT,
                        GameConstants.ITEM_COOLDOWNS.getOrDefault(Items.TRIDENT,
                                GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.KNIFE, 0)));
            }
            trident.discard();
        }
    }
}

package org.agmas.noellesroles.mixin.halloween;

import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.halloween.HalloweenLinkRules;
import org.agmas.noellesroles.game.halloween.HalloweenRuntime;
import org.agmas.noellesroles.game.halloween.HalloweenSoulLinks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 活动限定的共享盾池与禁盾；不复制护盾，也不改变到期时间。 */
@Mixin(SREArmorPlayerComponent.class)
public abstract class HalloweenArmorMixin {
    @Shadow @Final private Player player;

    @Inject(method = "getAllArmorCount", at = @At("HEAD"), cancellable = true)
    private void halloween$count(CallbackInfoReturnable<Integer> cir) {
        if (!HalloweenRuntime.isEnabled(player.level())) return;
        if (HalloweenRuntime.isShieldSuppressed(player)) { cir.setReturnValue(0); return; }
        Player partner = HalloweenSoulLinks.partner(player);
        if (partner != null) cir.setReturnValue(HalloweenLinkRules.sharedArmor(
                HalloweenSoulLinks.rawArmor(player), HalloweenRuntime.isShieldSuppressed(partner)
                        ? 0 : HalloweenSoulLinks.rawArmor(partner)));
    }

    @Inject(method = "triggerAndRemoveArmor", at = @At("HEAD"), cancellable = true)
    private void halloween$consume(int amount, CallbackInfoReturnable<Boolean> cir) {
        if (!HalloweenRuntime.isEnabled(player.level())) return;
        if (HalloweenRuntime.isShieldSuppressed(player)) { cir.setReturnValue(false); return; }
        Boolean consumed = HalloweenSoulLinks.consumeArmor(player, amount);
        if (consumed != null) cir.setReturnValue(consumed);
    }

    @Inject(method = "shouldSyncWith", at = @At("HEAD"), cancellable = true)
    private void halloween$sync(ServerPlayer target, CallbackInfoReturnable<Boolean> cir) {
        if (HalloweenSoulLinks.arePartners(player, target)) cir.setReturnValue(true);
    }
}

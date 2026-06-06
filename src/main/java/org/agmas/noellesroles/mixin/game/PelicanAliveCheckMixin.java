package org.agmas.noellesroles.mixin.game;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameUtils.class)
public class PelicanAliveCheckMixin {

    @Inject(method = "isPlayerAliveAndSurvivalIgnoreShitSplit", at = @At("HEAD"), cancellable = true)
    private static void pelicanStashedIsAlive(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (PelicanManager.isStashed(player)) {
            cir.setReturnValue(true);
        }
    }
}

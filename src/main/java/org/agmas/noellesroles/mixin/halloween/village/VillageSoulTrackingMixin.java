package org.agmas.noellesroles.mixin.halloween.village;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.halloween.HalloweenVillage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 仅亡魂夜向活动活人同步真正旁观者；亮度门槛在客户端逐帧判断。 */
@Mixin(ServerPlayer.class)
public abstract class VillageSoulTrackingMixin {
    @Inject(method = "broadcastToPlayer", at = @At("RETURN"), cancellable = true)
    private void halloween$trackSoul(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer soul = (ServerPlayer) (Object) this;
        if (!cir.getReturnValueZ() && soul.isSpectator() && soul != viewer && soul.level() == viewer.level()
                && GameUtils.isPlayerAliveAndSurvival(viewer) && HalloweenVillage.isGhostNight(viewer.level())) {
            cir.setReturnValue(true);
        }
    }
}

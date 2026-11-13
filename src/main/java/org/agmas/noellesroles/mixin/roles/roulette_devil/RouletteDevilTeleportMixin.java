package org.agmas.noellesroles.mixin.roles.roulette_devil;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import org.agmas.noellesroles.game.roles.neutral.roulette_devil.RouletteDevilSessions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 只锁定专属局参与者；管理器进出场通过 UUID 级短时豁免。 */
@Mixin(ServerPlayer.class)
public abstract class RouletteDevilTeleportMixin {
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void rouletteDevil$teleport(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relative,
            float yaw, float pitch, CallbackInfoReturnable<Boolean> cir) {
        if (RouletteDevilSessions.locked(((ServerPlayer) (Object) this).getUUID())) cir.setReturnValue(false);
    }
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void rouletteDevil$dimension(ServerLevel level, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        if (RouletteDevilSessions.locked(((ServerPlayer) (Object) this).getUUID())) ci.cancel();
    }
    @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
    private void rouletteDevil$staySeated(CallbackInfo ci) {
        if (RouletteDevilSessions.locked(((ServerPlayer) (Object) this).getUUID())) ci.cancel();
    }
}

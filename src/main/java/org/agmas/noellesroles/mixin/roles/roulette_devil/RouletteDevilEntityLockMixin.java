package org.agmas.noellesroles.mixin.roles.roulette_devil;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.game.roles.neutral.roulette_devil.RouletteDevilSessions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 防止直接 removeVehicle / setPos 绕过传送与离座限制。 */
@Mixin(Entity.class)
public abstract class RouletteDevilEntityLockMixin {
    @Inject(method = "removeVehicle", at = @At("HEAD"), cancellable = true)
    private void rouletteDevil$noDismount(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer p && RouletteDevilSessions.locked(p.getUUID())) ci.cancel();
    }
    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void rouletteDevil$noDirectEscape(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer p && RouletteDevilSessions.locked(p.getUUID())
                && !RouletteDevilSessions.positionAllowed(p, x, y, z)) ci.cancel();
    }
}

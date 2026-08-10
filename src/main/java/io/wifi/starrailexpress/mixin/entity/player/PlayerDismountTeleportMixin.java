package io.wifi.starrailexpress.mixin.entity.player;

import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class PlayerDismountTeleportMixin {
    @Inject(method = "dismountTo", at = @At("TAIL"))
    public void dismountTo(double d, double e, double f, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        player.teleportTo(d, e, f);
    }
}

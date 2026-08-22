/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.agmas.noellesroles.mixin.time_rewind;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.agmas.noellesroles.api.time.TimeRewind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents client movement packets from fighting the server rewind spline. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerTimeRewindMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void noellesroles$lockMovementDuringRewind(ServerboundMovePlayerPacket packet,
            CallbackInfo callbackInfo) {
        if (TimeRewind.isSmoothRewinding(player)) {
            callbackInfo.cancel();
        }
    }
}

package org.agmas.noellesroles.mixin.client.compat.inv_move;

import io.wifi.starrailexpress.client.SREClient;
import me.pieking1215.invmove.InvMove;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InvMove.class)
public class InvMoveBaned {
    @Inject(method = "tickKeybinds", at = @At("HEAD"), cancellable = true)
    private static void banInvMove(CallbackInfo ci) {
        if (SREClient.isInLobby()){
            ci.cancel();
        }
    }
}

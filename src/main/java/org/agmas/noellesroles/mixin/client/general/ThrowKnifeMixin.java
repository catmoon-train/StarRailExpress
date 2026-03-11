package org.agmas.noellesroles.mixin.client.general;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.TryThrowKnifePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class ThrowKnifeMixin
{
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    public void drop(boolean bl, CallbackInfoReturnable<Boolean> cir)
    {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (player.getMainHandItem().is(ModItems.THROWING_KNIFE)){
            ClientPlayNetworking.send(new TryThrowKnifePacket());
            cir.setReturnValue( false);
            cir.cancel();
        }
    }
}

package org.agmas.noellesroles.mixin.client.general;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.item.StalkerKnifeItem;
import org.agmas.noellesroles.packet.TryThrowItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class ThrowItemMixin
{
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    public void drop(boolean bl, CallbackInfoReturnable<Boolean> cir)
    {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (player.getMainHandItem().is(ModItems.THROWING_KNIFE) || player.getMainHandItem().getItem() instanceof StalkerKnifeItem){
            ClientPlayNetworking.send(new TryThrowItemPacket());
            cir.setReturnValue( false);
            cir.cancel();
        }
    }
}

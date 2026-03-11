package io.wifi.starrailexpress.mixin.client;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenMixin
{
    @Inject(method = "shouldCloseOnEsc", at = @At("HEAD"), cancellable = true)
    public void init(CallbackInfoReturnable<Boolean> cir)
    {
        cir.setReturnValue(true);
        cir.cancel();
    }

}

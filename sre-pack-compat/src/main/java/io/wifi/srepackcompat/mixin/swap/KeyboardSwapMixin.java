package io.wifi.srepackcompat.mixin.swap;

import io.wifi.srepackcompat.swap.InstantSwapClient;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardSwapMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void sreSwap$key(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (InstantSwapClient.key(window, key, scanCode, action)) ci.cancel();
    }
}

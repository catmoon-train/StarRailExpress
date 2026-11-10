package io.wifi.srepackcompat.mixin.cobblemon;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Deque;

@Mixin(ToastComponent.class)
public class ToastsMixin {
    @Shadow @Final private Deque<Toast> queued;

    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void sre$blockTutorial(Toast toast, CallbackInfo ci) {
        if (!CobblemonPolicy.enabled() && CobblemonPolicy.isPresentation(toast.getClass())) ci.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void sre$discardQueuedTutorials(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) queued.removeIf(toast -> CobblemonPolicy.isPresentation(toast.getClass()));
    }
}

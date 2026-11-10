package io.wifi.srepackcompat.mixin.cobblemon;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import net.minecraft.client.gui.components.toasts.Toast;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public class VisibleToastMixin {
    @Shadow @Final private Toast toast;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void sre$hideExistingTutorial(CallbackInfoReturnable<Boolean> cir) {
        if (!CobblemonPolicy.enabled() && CobblemonPolicy.isPresentation(toast.getClass())) cir.setReturnValue(true);
    }
}

package io.wifi.starrailexpress.mixin.compat.mecchachameleon;

import com.mecchachameleon.client.PaintEditorScreen;
import io.wifi.starrailexpress.compat.chameleon.ChameleonCompat;
import io.wifi.starrailexpress.compat.chameleon.SREPaintEditorScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PaintEditorScreen.class)
public class PaintEditorScreenMixin {
    @Inject(method = "open", at = @At("HEAD"), cancellable = true)
    private static void onOpen(CallbackInfo ci) {
        SREPaintEditorScreen.open();
        ci.cancel();
    }
}

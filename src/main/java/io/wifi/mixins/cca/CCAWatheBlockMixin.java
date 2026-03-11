package io.wifi.mixins.cca;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.function.Consumer;

@Mixin(targets = "org.ladysnake.cca.internal.base.asm.StaticComponentPluginBase", remap = false)
public class CCAWatheBlockMixin {

    @Inject(method = "processInitializers", at = @At("HEAD"), cancellable = true, remap = false)
    private static void skipWathe(Collection<?> entrypoints, Consumer<?> consumer, CallbackInfo ci) {
        // 检查 entrypoints 里是否包含 wathe 的类，有则跳过
        for (Object ep : entrypoints) {
            if (ep.getClass().getName().startsWith("dev.doctor4t.wathe.")) {
                ci.cancel();
                return;
            }
        }
    }
}
package io.wifi.sreperf.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.Window;
import io.wifi.sreperf.CaptureController;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Window.class)
abstract class WindowMixin {
    @WrapMethod(method = "updateDisplay")
    private void sreperf$swap(Operation<Void> original) {
        long token = CaptureController.begin("frame_swap");
        try { original.call(); }
        finally { CaptureController.end("frame_swap", token, 0); }
    }
}

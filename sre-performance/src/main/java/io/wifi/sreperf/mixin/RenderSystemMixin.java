package io.wifi.sreperf.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.sreperf.CaptureController;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderSystem.class)
abstract class RenderSystemMixin {
    @WrapMethod(method = "limitDisplayFPS")
    private static void sreperf$limit(int fps, Operation<Void> original) {
        long token = CaptureController.begin("limit_fps");
        try { original.call(fps); }
        finally { CaptureController.end("limit_fps", token, 0); }
    }
}

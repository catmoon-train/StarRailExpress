package io.wifi.sreperf.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.sreperf.CaptureController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @WrapMethod(method = "render")
    private void sreperf$render(DeltaTracker delta, boolean renderLevel, Operation<Void> original) {
        long token = CaptureController.begin("render");
        try { original.call(delta, renderLevel); }
        finally { CaptureController.end("render", token, 0); }
    }

    @WrapMethod(method = "renderLevel")
    private void sreperf$world(DeltaTracker delta, Operation<Void> original) {
        long token = CaptureController.begin("world_render");
        try { original.call(delta); }
        finally { CaptureController.end("world_render", token, 0); }
    }
}

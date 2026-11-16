package io.wifi.sreperf.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.sreperf.CaptureController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @WrapMethod(method = "runTick")
    private void sreperf$frame(boolean renderLevel, Operation<Void> original) {
        int ticket = CaptureController.frameStart();
        boolean success = false;
        try { original.call(renderLevel); success = true; }
        finally { CaptureController.frameEnd(ticket, success); }
    }

    @WrapMethod(method = "tick")
    private void sreperf$tick(Operation<Void> original) {
        long token = CaptureController.begin("client_tick");
        try { original.call(); }
        finally { CaptureController.end("client_tick", token, 0); }
    }
}

package io.wifi.srepackcompat.mixin.runtime;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.srepackcompat.render.HybridFramePacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public abstract class FramePacerMixin {
    @Unique private static final HybridFramePacer sreCompat$pacer = new HybridFramePacer();

    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true)
    private static void sreCompat$pace(int fps, CallbackInfo ci) {
        if (fps <= 0) return;
        sreCompat$pacer.pace(fps);
        ci.cancel();
    }
}

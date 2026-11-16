package io.wifi.sreperf.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.sreperf.CaptureController;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TextureManager.class)
abstract class TextureManagerMixin {
    @WrapMethod(method = "tick")
    private void sreperf$animation(Operation<Void> original) {
        long token = CaptureController.begin("texture_animation");
        try { original.call(); }
        finally { CaptureController.end("texture_animation", token, 0); }
    }
}

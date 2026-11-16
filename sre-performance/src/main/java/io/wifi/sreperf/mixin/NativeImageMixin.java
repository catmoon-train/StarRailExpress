package io.wifi.sreperf.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.NativeImage;
import io.wifi.sreperf.CaptureController;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NativeImage.class)
abstract class NativeImageMixin {
    @WrapMethod(method = "_upload")
    private void sreperf$upload(int level, int x, int y, int skipX, int skipY, int width, int height,
                                boolean blur, boolean clamp, boolean mipmap, boolean close, Operation<Void> original) {
        long token = CaptureController.begin("texture_upload");
        long bytes = token == 0 ? 0 : (long) Math.max(0, width) * Math.max(0, height) * ((NativeImage) (Object) this).format().components();
        try { original.call(level, x, y, skipX, skipY, width, height, blur, clamp, mipmap, close); }
        finally { CaptureController.end("texture_upload", token, bytes); }
    }
}

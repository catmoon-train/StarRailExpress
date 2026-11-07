package io.wifi.starrailexpress.mixin.compat.iris;

import io.wifi.starrailexpress.client.render.ShaderPackFog;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 光影完成调色、泛光等世界合成后再补雾，避免包内自定义雾忽略 SRE 的视野限制。 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisShaderPackFogMixin {
    @Inject(method = "beginLevelRendering", at = @At("HEAD"))
    private void sre$resetFog(CallbackInfo ci) {
        ShaderPackFog.beginFrame();
    }

    @Inject(method = "finalizeLevelRendering", at = @At("RETURN"))
    private void sre$applyFog(CallbackInfo ci) {
        var state = CapturedRenderingState.INSTANCE;
        ShaderPackFog.render(state.getGbufferProjection(), state.getGbufferModelView());
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void sre$releaseFog(CallbackInfo ci) {
        ShaderPackFog.destroy();
    }
}

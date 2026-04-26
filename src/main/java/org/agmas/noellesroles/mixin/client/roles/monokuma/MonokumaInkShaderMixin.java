package org.agmas.noellesroles.mixin.client.roles.monokuma;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.agmas.noellesroles.client.TimeStopShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MonokumaInkShaderMixin {

    @SuppressWarnings("resource")
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    private void renderMonokumaInk(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        GameRenderer renderer = (GameRenderer) (Object) this;
        if (renderer != null && bl && renderer.getMinecraft().level != null) {
            TimeStopShader.instance.initPostProcessor();
            TimeStopShader.instance.renderPostProcess(deltaTracker.getGameTimeDeltaPartialTick(true));
        }
    }

    @Inject(method = "resize(II)V", at = @At("TAIL"))
    private void resizeMonokumaInk(int pWidth, int pHeight, CallbackInfo ci) {
        if (TimeStopShader.instance != null) {
            TimeStopShader.instance.resize(pWidth, pHeight);
        }
    }
}

package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.agmas.noellesroles.client.ImmersiveFilterShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class ImmersiveFilterShaderMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    private void renderImmersive(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        try (GameRenderer renderer = (GameRenderer) (Object) this) {
            if (renderer != null && bl && renderer.getMinecraft().level != null) {
                ImmersiveFilterShader.instance.initPostProcessor();
                ImmersiveFilterShader.instance.renderPostProcess(deltaTracker.getGameTimeDeltaPartialTick(true));
            }
        }
    }

    @Inject(method = "resize(II)V", at = @At("TAIL"))
    private void resizeImmersive(int w, int h, CallbackInfo ci) {
        ImmersiveFilterShader.instance.resize(w, h);
    }
}

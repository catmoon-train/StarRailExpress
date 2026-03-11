package org.agmas.noellesroles.mixin.client.time_stop;

import io.wifi.starrailexpress.client.SansRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.agmas.noellesroles.client.TimeStopShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.agmas.noellesroles.client.TimeStopShader.instance;


@Mixin(GameRenderer.class)
public class GameRendererMixin
{
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    private void render(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci)
    {
        GameRenderer renderer = (GameRenderer)(Object)this;

        if (renderer != null&& bl && renderer.getMinecraft().level != null)
        {
            TimeStopShader gui = instance;
            gui.initPostProcessor();
            gui.renderPostProcess(deltaTracker.getGameTimeDeltaPartialTick(true));
        }
    }
    @Inject(method = "resize(II)V", at = @At("TAIL"))
    private void resize(int pWidth, int pHeight, CallbackInfo ci)
    {
        if (instance != null)
            instance.resize(pWidth, pHeight);
    }
}

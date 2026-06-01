package io.wifi.starrailexpress.mixin.client.ui;

import org.agmas.noellesroles.client.hud.CommonClientHudRenderer;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)

public class RenderEffectMixin {
    @Inject(method = "renderEffects", at = @At("HEAD"))
    private void sre$moveEffectPostion_head(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, CommonClientHudRenderer.effectStartY, 0);
    }

    @Inject(method = "renderEffects", at = @At(value = "RETURN"))
    private void sre$moveEffectPostion_tail(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        guiGraphics.pose().popPose();
    }
}

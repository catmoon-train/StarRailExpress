package io.wifi.srepackcompat.mixin.jei;

import io.wifi.srepackcompat.jei.JeiLobbyGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 不取消原版界面绘制，只阻止 JEI 自身的叠加层及其布局接管。 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.events.GuiEventHandler", remap = false)
public class GuiEventHandlerMixin {
    @Inject(method = {"updateForScreenRender", "drawForScreenBackground", "drawForScreenForeground", "onClientTick"},
            at = @At("HEAD"), cancellable = true)
    private void sre$overlay(CallbackInfo ci) { if (!JeiLobbyGate.enabled()) ci.cancel(); }

    @Inject(method = "renderCompactPotionIndicators", at = @At("HEAD"), cancellable = true)
    private void sre$vanillaEffects(CallbackInfoReturnable<Boolean> cir) {
        if (!JeiLobbyGate.enabled()) cir.setReturnValue(false);
    }
}

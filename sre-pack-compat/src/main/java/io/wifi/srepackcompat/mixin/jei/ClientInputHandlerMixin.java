package io.wifi.srepackcompat.mixin.jei;

import io.wifi.srepackcompat.jei.JeiLobbyGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** false 表示 JEI 未消费事件，让原版背包与列车交互继续处理。 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.input.ClientInputHandler", remap = false)
public class ClientInputHandlerMixin {
    @Inject(method = {"onKeyboardKeyPressedPre", "onKeyboardKeyPressedPost", "onKeyboardCharTypedPre",
            "onGuiMouseClicked", "onGuiMouseReleased", "onGuiMouseScroll", "onGuiMouseDragged"},
            at = @At("HEAD"), cancellable = true)
    private void sre$passInput(CallbackInfoReturnable<Boolean> cir) {
        if (!JeiLobbyGate.enabled()) cir.setReturnValue(false);
    }
    @Inject(method = "onKeyboardCharTypedPost", at = @At("HEAD"), cancellable = true)
    private void sre$noSearchTyping(CallbackInfo ci) { if (!JeiLobbyGate.enabled()) ci.cancel(); }
}

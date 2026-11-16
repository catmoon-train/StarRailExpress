package io.wifi.srepackcompat.mixin.jei;

import io.wifi.srepackcompat.jei.JeiLobbyGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 同时覆盖普通与 AMECS 按键的 JEI 包装层，禁止大厅外触发。 */
@Pseudo
@Mixin(targets = "mezz.jei.fabric.input.FabricJeiKeyMapping", remap = false)
public class JeiKeyMappingMixin {
    @Inject(method = {"isActiveAndMatches", "isActiveAndMatchesAllowingExtraModifiers", "isDown"},
            at = @At("HEAD"), cancellable = true)
    private void sre$inactive(CallbackInfoReturnable<Boolean> cir) {
        if (!JeiLobbyGate.enabled()) cir.setReturnValue(false);
    }
}

package io.wifi.srepokemoncompat.mixin.mega.client;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 禁止把 Mega 按钮加入交互轮盘，原有输入和关闭界面逻辑保持不变。 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.client.CobbleClientEvents", remap = false)
public abstract class MegaInteractionUiMixin {
    @Inject(method = "addGimmickButtons", at = @At("HEAD"), cancellable = true, require = 1)
    private static void sre$noMegaButtons(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) ci.cancel();
    }
}

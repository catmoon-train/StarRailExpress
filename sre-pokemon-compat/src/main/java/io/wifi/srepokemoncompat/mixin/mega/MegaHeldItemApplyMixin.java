package io.wifi.srepokemoncompat.mixin.mega;

import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** heldItemChange 同时负责移除旧效果；仅暂停 apply，保留全部 revert 分支。 */
@Pseudo
@Mixin(targets = {
        "com.github.yajatkaul.mega_showdown.api.codec.HeldItemFormChange",
        "com.github.yajatkaul.mega_showdown.item.custom.form_change.FormChangeHeldItem",
        "com.github.yajatkaul.mega_showdown.item.custom.form_change.FormChangeHeldItemHidden"
}, remap = false)
public abstract class MegaHeldItemApplyMixin {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true, require = 1)
    private void sre$newHeldEffect(CallbackInfo ci) {
        if (!CobblemonMechanics.enabled()) ci.cancel();
    }
}

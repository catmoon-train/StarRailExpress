package io.wifi.starrailexpress.mixin.client.vc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.maxhenkel.voicechat.configbuilder.entry.DoubleConfigEntry;

@Mixin(value = DoubleConfigEntry.class)
public abstract class DoubleConfigEntryMixin {

    @Inject(method = "fixValue", at = @At("HEAD"), cancellable = true)
    private void modifyMax(Double value, CallbackInfoReturnable<Double> cir) {
        var entry = (DoubleConfigEntry) (Object) this;
        cir.setReturnValue(Math.max(Math.min(value, (Double) entry.getMax()), (Double) entry.getMin()));
        return;
    }
}
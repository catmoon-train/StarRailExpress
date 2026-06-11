package io.wifi.starrailexpress.mixin.client.vc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.maxhenkel.voicechat.configbuilder.entry.AbstractRangedConfigEntry;

@Mixin(value = AbstractRangedConfigEntry.class)
public abstract class BetterVoiceChatVolumeManageMixin<T> {
    
    @SuppressWarnings("unchecked")
    @Inject(
        method = "getMax",
        at = @At("RETURN"),
        cancellable = true
    )
    private void modifyMax(CallbackInfoReturnable<T> cir) {
        var entry = (AbstractRangedConfigEntry<T>)(Object)this;
        if ("voice_chat_volume".equals(entry.getKey()) && cir.getReturnValue() instanceof Double) {
            cir.setReturnValue((T) Double.valueOf(10.0));
            return;
        }
    }
}
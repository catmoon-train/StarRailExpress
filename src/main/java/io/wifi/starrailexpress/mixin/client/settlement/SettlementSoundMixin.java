package io.wifi.starrailexpress.mixin.client.settlement;

import io.wifi.starrailexpress.settlement.client.SettlementAudio;
import io.wifi.starrailexpress.settlement.client.SettlementClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 包括网络音、环境音和延迟音在内，统一在最终 SoundEngine 边界过滤。 */
@Mixin(value = SoundEngine.class, priority = 2500)
public abstract class SettlementSoundMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void settlement$sound(SoundInstance sound, CallbackInfo ci) {
        if (SettlementClient.isActive() && !SettlementAudio.allowed(sound)) ci.cancel();
    }
}

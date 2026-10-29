package org.agmas.noellesroles.mixin.halloween.ambience;

import net.minecraft.client.sounds.MusicManager;
import org.agmas.noellesroles.game.halloween.ambience.HalloweenAmbienceClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只暂停活动局的原版随机选曲；退出后原版计时自然继续，不停唱片或语音。 */
@Mixin(MusicManager.class)
public abstract class HalloweenMusicManagerMixin {
    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void halloween$pauseRandomMusic(CallbackInfo ci) {
        if (HalloweenAmbienceClient.suppressVanillaMusic()) ci.cancel();
    }
}

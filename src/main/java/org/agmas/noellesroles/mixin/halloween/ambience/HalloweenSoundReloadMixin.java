package org.agmas.noellesroles.mixin.halloween.ambience;

import net.minecraft.client.sounds.SoundEngine;
import org.agmas.noellesroles.game.halloween.ambience.HalloweenAmbienceClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SoundManager.apply 和 F3+S 都经过这里，避免重载后保留已失效的流实例。 */
@Mixin(SoundEngine.class)
public abstract class HalloweenSoundReloadMixin {
    @Inject(method = "reload()V", at = @At("HEAD"))
    private void halloween$releaseOldStream(CallbackInfo ci) {
        HalloweenAmbienceClient.onSoundReload();
    }
}

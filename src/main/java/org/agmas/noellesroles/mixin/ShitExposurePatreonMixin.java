// Patreon
package org.agmas.noellesroles.mixin;

import java.net.URI;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.mortuusars.exposure.util.supporter.Gilded;

/**
 * 玩家重置 Mixin
 * 
 * 在游戏结束时（GameFunctions.resetPlayer 被调用）清除所有自定义组件的状态
 * 这确保了下一局游戏开始时玩家不会有残留的状态
 */
@Mixin(Gilded.class)
public abstract class ShitExposurePatreonMixin {
    @Inject(method = "getUuidsUri", at = @At("TAIL"), order = 10000, cancellable = true)
    private void shitExposure(CallbackInfoReturnable<URI> cir){
        cir.setReturnValue(null);
        cir.cancel();
    }
}
package io.wifi.srepackcompat.mixin.runtime;

import com.mojang.authlib.yggdrasil.TextureUrlChecker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 允许第三方皮肤源；仅改变 Authlib 的纹理域名判定，下载流程仍使用原实现。 */
@Mixin(value = TextureUrlChecker.class, remap = false)
public abstract class TextureUrlMixin {
    @Inject(method = "isAllowedTextureDomain", at = @At("HEAD"), cancellable = true)
    private static void sreCompat$allowSkinSource(String url, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}

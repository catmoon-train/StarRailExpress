package io.wifi.srepackcompat.mixin.runtime;

import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo;
import io.wifi.srepackcompat.skin.SkinTexturePolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = YggdrasilServicesKeyInfo.class, remap = false)
public abstract class TextureServicesKeyMixin {
    @Inject(method = "validateProperty", at = @At("HEAD"), cancellable = true)
    private void sreCompat$allowTextureProperty(Property property, CallbackInfoReturnable<Boolean> cir) {
        if (property != null && SkinTexturePolicy.isTextureProperty(property.name())) cir.setReturnValue(true);
    }
}

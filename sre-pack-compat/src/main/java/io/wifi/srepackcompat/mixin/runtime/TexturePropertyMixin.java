package io.wifi.srepackcompat.mixin.runtime;

import com.mojang.authlib.properties.Property;
import io.wifi.srepackcompat.skin.SkinTexturePolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Property.class, remap = false)
public abstract class TexturePropertyMixin {
    @Inject(method = "isSignatureValid", at = @At("HEAD"), cancellable = true)
    private void sreCompat$allowTextureProperty(CallbackInfoReturnable<Boolean> cir) {
        if (SkinTexturePolicy.isTextureProperty(((Property) (Object) this).name())) cir.setReturnValue(true);
    }
}

package io.wifi.srepackcompat.mixin.chloride;

import io.wifi.srepackcompat.config.ChlorideOptionPolicy;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.client.config.value.ConstantValue;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.config.builder.StatefulOptionBuilderImpl", remap = false)
public abstract class ChlorideOptionBindingMixin<T> {
    @Unique private ResourceLocation sreCompat$id;
    @Shadow public abstract OptionBinding<T> getBinding();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sreCompat$id(ResourceLocation id, CallbackInfo ci) { sreCompat$id = id; }

    @Inject(method = "getBinding", at = @At("RETURN"), cancellable = true)
    private void sreCompat$binding(CallbackInfoReturnable<OptionBinding<T>> cir) {
        if (cir.getReturnValue() != null) cir.setReturnValue(ChlorideOptionPolicy.binding(sreCompat$id, cir.getReturnValue()));
    }

    @Inject(method = "getDefaultValue", at = @At("RETURN"), cancellable = true)
    private void sreCompat$defaults(CallbackInfoReturnable<DependentValue<T>> cir) {
        if (ChlorideOptionPolicy.isLocked(sreCompat$id) && getBinding() != null) {
            cir.setReturnValue(new ConstantValue<>(getBinding().load()));
        }
    }

    @Inject(method = "getControlHiddenWhenDisabled", at = @At("RETURN"), cancellable = true)
    private void sreCompat$visible(CallbackInfoReturnable<Boolean> cir) {
        if (ChlorideOptionPolicy.isLocked(sreCompat$id)) cir.setReturnValue(false);
    }
}

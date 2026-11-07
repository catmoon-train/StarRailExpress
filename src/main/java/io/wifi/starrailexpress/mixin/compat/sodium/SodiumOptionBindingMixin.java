package io.wifi.starrailexpress.mixin.compat.sodium;

import io.wifi.starrailexpress.client.SodiumOptionPolicy;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;
import net.caffeinemc.mods.sodium.client.config.value.ConstantValue;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 约束配置绑定及“重置为默认”；禁用控件之外仍不允许 API 写入受限值。 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.config.builder.StatefulOptionBuilderImpl", remap = false)
public abstract class SodiumOptionBindingMixin<T> {
    @Unique private ResourceLocation sre$id;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sre$captureId(ResourceLocation id, CallbackInfo ci) { sre$id = id; }

    @Inject(method = "getBinding", at = @At("RETURN"), cancellable = true)
    private void sre$lockBinding(CallbackInfoReturnable<OptionBinding<T>> cir) {
        if (cir.getReturnValue() != null) cir.setReturnValue(SodiumOptionPolicy.binding(sre$id, cir.getReturnValue()));
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "getDefaultValue", at = @At("RETURN"), cancellable = true)
    private void sre$lockDefault(CallbackInfoReturnable<DependentValue<T>> cir) {
        Object fixed = SodiumOptionPolicy.lockedValue(sre$id);
        if (fixed != null) cir.setReturnValue(new ConstantValue<>((T) fixed));
    }

    @Inject(method = "getControlHiddenWhenDisabled", at = @At("RETURN"), cancellable = true)
    private void sre$keepLockedControlsVisible(CallbackInfoReturnable<Boolean> cir) {
        if (SodiumOptionPolicy.isLocked(sre$id)) cir.setReturnValue(SodiumOptionPolicy.isHidden(sre$id));
    }
}

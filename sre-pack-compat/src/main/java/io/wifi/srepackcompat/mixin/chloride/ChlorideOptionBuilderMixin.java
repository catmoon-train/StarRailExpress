package io.wifi.srepackcompat.mixin.chloride;

import io.wifi.srepackcompat.config.ChlorideOptionPolicy;
import net.caffeinemc.mods.sodium.client.config.value.ConstantValue;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 只禁用 Chloride 命名空间控件，不影响 Sodium 及其他模组的设置。 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.config.builder.OptionBuilderImpl", remap = false)
public abstract class ChlorideOptionBuilderMixin {
    @Shadow @Final ResourceLocation id;

    @Inject(method = "getEnabled", at = @At("HEAD"), cancellable = true)
    private void sreCompat$disabled(CallbackInfoReturnable<DependentValue<Boolean>> cir) {
        if (ChlorideOptionPolicy.isLocked(id)) cir.setReturnValue(new ConstantValue<>(false));
    }
}

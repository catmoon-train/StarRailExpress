package io.wifi.starrailexpress.mixin.compat.sodium;

import io.wifi.starrailexpress.client.SodiumOptionPolicy;
import net.caffeinemc.mods.sodium.client.config.value.ConstantValue;
import net.caffeinemc.mods.sodium.client.config.value.DependentValue;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium 与 Extra 共用的 0.8 配置构建入口，禁止后续 enabledProvider 再次开放受限控件。 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.config.builder.OptionBuilderImpl", remap = false)
public abstract class SodiumOptionBuilderMixin {
    @Shadow @Final ResourceLocation id;

    @Inject(method = "getEnabled", at = @At("HEAD"), cancellable = true)
    private void sre$disableRestrictedOption(CallbackInfoReturnable<DependentValue<Boolean>> cir) {
        if (SodiumOptionPolicy.isLocked(id)) cir.setReturnValue(new ConstantValue<>(false));
    }
}

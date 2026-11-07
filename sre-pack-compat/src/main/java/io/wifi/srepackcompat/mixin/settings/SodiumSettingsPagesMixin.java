package io.wifi.srepackcompat.mixin.settings;

import io.wifi.srepackcompat.config.SodiumSettingsPages;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Sodium / Reese 共用的页面展示列表，不干预配置收集与依赖校验。 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.config.structure.Config", remap = false)
public abstract class SodiumSettingsPagesMixin {
    @Inject(method = "getModOptions", at = @At("RETURN"), cancellable = true)
    private void sreCompat$visiblePages(CallbackInfoReturnable<List<ModOptions>> cir) {
        cir.setReturnValue(SodiumSettingsPages.visible(cir.getReturnValue()));
    }
}

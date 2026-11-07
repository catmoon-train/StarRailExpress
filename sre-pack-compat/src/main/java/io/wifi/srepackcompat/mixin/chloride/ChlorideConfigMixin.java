package io.wifi.srepackcompat.mixin.chloride;

import io.wifi.srepackcompat.config.ChlorideOptionPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 正常加载完成后恢复内置值；保存前恢复，避免设置页面将临时值写盘。 */
@Pseudo
@Mixin(targets = "me.srrapero720.chloride.ChlorideConfig", remap = false)
public abstract class ChlorideConfigMixin {
    @Inject(method = "load", at = @At("RETURN"))
    private static void sreCompat$loaded(CallbackInfo ci) { ChlorideOptionPolicy.enforce(); }

    @Inject(method = "write", at = @At("HEAD"))
    private static void sreCompat$writing(CallbackInfo ci) { ChlorideOptionPolicy.enforce(); }
}

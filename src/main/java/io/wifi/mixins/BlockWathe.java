package io.wifi.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.doctor4t.wathe.Wathe;
import io.wifi.starrailexpress.SRE;

@Mixin(Wathe.class)
public class BlockWathe {
    @Inject(method = "<clinit>", at = @At("HEAD"), cancellable = true)
    private static void cancelInit(CallbackInfo ci) {
        ci.cancel();
    }

    @Overwrite(remap = false)
    public void onInitialize() {
        SRE.LOGGER.info("Block WATHE SERVER!!!");
        // 空实现 —— 阻断所有方块/物品注册、配方、事件监听等
    }
}

package io.wifi.mixins.client;

import dev.doctor4t.wathe.client.WatheClient;
import io.wifi.starrailexpress.SRE;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WatheClient.class)
public class BlockWatheClient {
    @Inject(method = "<clinit>", at = @At("HEAD"), cancellable = true)
    private static void cancelInit(CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * @author yourmod
     * @reason 仅使用该 mod 的材质资源，禁用所有客户端逻辑
     */
    @Overwrite(remap = false)
    public void onInitializeClient() {
        SRE.LOGGER.info("Block WATHE CLIENT!!!");
        // 空实现 —— 阻断渲染器注册、粒子、HUD 等
    }
}

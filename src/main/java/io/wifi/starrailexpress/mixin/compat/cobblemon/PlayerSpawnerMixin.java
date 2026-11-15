package io.wifi.starrailexpress.mixin.compat.cobblemon;

import io.wifi.starrailexpress.SREConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 服务端关闭宝可梦时停止自然刷新计时和选点，重载配置后可直接恢复。 */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner", remap = false)
public class PlayerSpawnerMixin {
    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void sre$pauseNaturalSpawning(CallbackInfo ci) {
        if (!SREConfig.instance().pokmen) ci.cancel();
    }
}

package io.wifi.starrailexpress.mixin.compat.dashloader;

import io.wifi.starrailexpress.client.resource.DashLoaderCompat;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 复用原版 F3+T，不注册新按键；手动重载必须能绕过烘焙缓存并重新检查文件。 */
@Mixin(KeyboardHandler.class)
public abstract class DashDebugReloadMixin {
    @Inject(method = "handleDebugKeys", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/Minecraft;reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;"))
    private void sre$forceFreshDebugReload(int key, CallbackInfoReturnable<Boolean> cir) {
        DashLoaderCompat.requestFreshReload();
    }
}

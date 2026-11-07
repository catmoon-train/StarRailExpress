package io.wifi.starrailexpress.mixin.compat.dashloader;

import com.bawnorton.mixinsquared.TargetHandler;
import io.wifi.starrailexpress.client.resource.DashLoaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 禁用列车加载层时也使用同一保存屏障，不启动原版 DashLoader 的无保护保存线程。 */
@Mixin(value = LoadingOverlay.class, priority = 70000)
public abstract class DashVanillaOverlayMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;

    @TargetHandler(mixin = "dev.notalpha.dashloader.mixin.main.SplashScreenMixin", name = "done", prefix = "handler")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
    private void sre$shareSaveBarrier(GuiGraphics graphics, int x, int y, float delta,
                                     CallbackInfo originalCi, CallbackInfo ci) {
        if (minecraft.getOverlay() != (Object) this) {
            // 原版失败回调已安装恢复层时，不重置恢复流程正在使用的缓存。
            ci.cancel();
            return;
        }
        boolean success = true;
        try {
            reload.checkExceptions();
        } catch (Throwable failure) {
            success = false;
        }
        DashLoaderCompat.finishReload(success);
        // 失败回调可能已创建恢复加载层，不清除新层，也不重建/覆盖自定义标题画面。
        if (minecraft.getOverlay() == (Object) this) {
            minecraft.setOverlay(null);
        }
        ci.cancel();
    }
}

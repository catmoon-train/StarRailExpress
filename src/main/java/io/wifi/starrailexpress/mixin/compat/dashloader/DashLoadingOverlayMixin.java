package io.wifi.starrailexpress.mixin.compat.dashloader;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.resource.DashLoaderCompat;
import net.exmo.sre.loading.StarRailLoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.function.Consumer;

/** 列车加载层替代原版 SplashScreen 后，补上原本由 DashLoader Splash Mixin 触发的保存。 */
@Mixin(value = StarRailLoadingOverlay.class, remap = false)
public abstract class DashLoadingOverlayMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"), remap = false)
    private void sre$persistCompletedReload(Consumer<Optional<Throwable>> consumer, Object result, Operation<Void> original) {
        DashLoaderCompat.finishReload(((Optional<?>) result).isEmpty());
        original.call(consumer, result);
    }
}

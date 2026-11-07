package io.wifi.starrailexpress.mixin.compat.dashloader;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.client.resource.DashLoaderCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

import java.util.concurrent.CompletableFuture;

/** 转服或后续资源包重载不能在缓存仍序列化时清空同一批 DashLoader 数据。 */
@Mixin(Minecraft.class)
public abstract class DashMinecraftReloadMixin {
    @WrapMethod(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<Void> sre$reloadAfterCacheSave(Operation<CompletableFuture<Void>> original) {
        return DashLoaderCompat.afterSave(original::call, (Minecraft) (Object) this);
    }
}

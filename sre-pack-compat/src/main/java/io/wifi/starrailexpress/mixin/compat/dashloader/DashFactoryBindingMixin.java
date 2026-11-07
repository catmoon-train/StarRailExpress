package io.wifi.starrailexpress.mixin.compat.dashloader;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.resource.CacheFailureCause;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** 嵌套 multipart/weighted 模型不支持序列化时，保留 ModelModule 原有的逐模型回退。 */
@Pseudo
@Mixin(targets = "dev.notalpha.dashloader.registry.FactoryBinding", remap = false)
public abstract class DashFactoryBindingMixin {
    @WrapOperation(method = "create(Ljava/lang/Object;Ldev/notalpha/dashloader/api/registry/RegistryWriter;)Ldev/notalpha/dashloader/api/DashObject;",
            at = @At(value = "NEW", target = "(Ljava/lang/String;Ljava/lang/Throwable;)Ljava/lang/RuntimeException;"),
            remap = false)
    private RuntimeException sre$preserveUnsupportedModel(String message, Throwable cause,
                                                         Operation<RuntimeException> original) {
        RuntimeException unsupported = CacheFailureCause.find(cause,
                "dev.notalpha.dashloader.api.registry.RegistryAddException");
        return unsupported != null ? unsupported : original.call(message, cause);
    }
}

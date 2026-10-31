package io.wifi.starrailexpress.mixin.compat.lightspeed;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.client.resource.ArchiveFileUri;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.net.URI;
import java.nio.file.Path;

/** 修复 Lightspeed 2.0.5 对含空格 JAR 路径二次转义导致的索引失效。 */
@Pseudo
@Mixin(targets = "com.ccr4ft3r.lightspeed.cache.resource.PackPathIndex", remap = false)
public abstract class PackPathIndexMixin {
    @WrapOperation(method = "identity", at = @At(value = "INVOKE", target =
            "Ljava/nio/file/Path;of(Ljava/net/URI;)Ljava/nio/file/Path;"), require = 0, remap = false)
    private static Path sre$decodeNestedFileUri(URI fileUri, Operation<Path> original,
                                               @Local(ordinal = 0) URI packRootUri) {
        return original.call(ArchiveFileUri.resolve(packRootUri, fileUri));
    }
}

package io.wifi.starrailexpress.mixin.compat.dashloader;

import io.wifi.starrailexpress.client.resource.PackFingerprintSource;
import io.wifi.starrailexpress.client.resource.ResourceFingerprint;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

/** Fabric 模组包直接复用 JAR 内容指纹，不重复展开遍历其中所有资源。 */
@Mixin(targets = "net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack", remap = false)
public abstract class DashFabricPackMixin implements PackFingerprintSource {
    @Shadow @Final private List<Path> basePaths;

    @Override
    public void sre$appendFingerprint(MessageDigest digest) throws IOException {
        for (Path path : basePaths) {
            ResourceFingerprint.path(digest, path);
        }
    }
}

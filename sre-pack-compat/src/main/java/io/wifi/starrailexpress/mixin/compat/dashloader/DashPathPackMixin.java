package io.wifi.starrailexpress.mixin.compat.dashloader;

import io.wifi.starrailexpress.client.resource.PackFingerprintSource;
import io.wifi.starrailexpress.client.resource.ResourceFingerprint;
import net.minecraft.server.packs.PathPackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;

/** 目录包需检查内部文件的新增、删除和编辑，不能只检查目录 mtime。 */
@Mixin(PathPackResources.class)
public abstract class DashPathPackMixin implements PackFingerprintSource {
    @Shadow @Final private Path root;

    @Override
    public void sre$appendFingerprint(MessageDigest digest) throws IOException {
        ResourceFingerprint.path(digest, root);
    }
}

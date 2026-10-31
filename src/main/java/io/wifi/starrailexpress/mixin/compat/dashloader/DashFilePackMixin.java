package io.wifi.starrailexpress.mixin.compat.dashloader;

import io.wifi.starrailexpress.client.resource.PackFingerprintSource;
import io.wifi.starrailexpress.client.resource.ResourceFingerprint;
import net.minecraft.server.packs.FilePackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.io.IOException;
import java.security.MessageDigest;

/** ZIP 的内容和内部 overlay 前缀参与缓存键，不再只识别旧版名为 server 的包。 */
@Mixin(FilePackResources.class)
public abstract class DashFilePackMixin implements PackFingerprintSource {
    @Shadow @Final private FilePackResources.SharedZipFileAccess zipFileAccess;
    @Shadow @Final private String prefix;

    @Override
    public void sre$appendFingerprint(MessageDigest digest) throws IOException {
        ResourceFingerprint.text(digest, prefix);
        ResourceFingerprint.path(digest, ((DashZipFileAccessor) (Object) zipFileAccess).sre$file().toPath());
    }
}

package io.wifi.starrailexpress.mixin.compat.dashloader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

/** 只读取 ZIP 文件来源，不打开或关闭资源管理器持有的 ZipFile。 */
@Mixin(targets = "net.minecraft.server.packs.FilePackResources$SharedZipFileAccess")
public interface DashZipFileAccessor {
    @Accessor("file")
    File sre$file();
}

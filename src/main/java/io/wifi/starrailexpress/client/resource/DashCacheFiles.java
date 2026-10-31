package io.wifi.starrailexpress.client.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 只有最终提交的元数据存在且含数据时，图集目录才构成可读取的 DashLoader 缓存。 */
public final class DashCacheFiles {
    private DashCacheFiles() { }

    public static boolean complete(Path cache) {
        Path metadata = cache.resolve("metadata.bin");
        try {
            return Files.isRegularFile(metadata) && Files.size(metadata) > 5;
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }
}

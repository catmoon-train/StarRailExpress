package io.wifi.starrailexpress.mixin.client;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.resource.ServerPackCache;
import net.minecraft.server.packs.DownloadQueue;
import net.minecraft.util.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

/** 仅改变后台下载的缓存目录，不干预确认、哈希校验、包顺序或服务器加载反馈。 */
@Mixin(DownloadQueue.class)
public abstract class ServerPackDownloadCacheMixin {
    @Unique
    private static final Logger sre$cacheLogger = LoggerFactory.getLogger("SRE/ServerPackCache");

    // 原版此调用位于一个编译器生成的下载 lambda 内，用调用目标定位而非依赖 lambda 名称。
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/util/HttpUtil;downloadFile(Ljava/nio/file/Path;Ljava/net/URL;Ljava/util/Map;Lcom/google/common/hash/HashFunction;Lcom/google/common/hash/HashCode;ILjava/net/Proxy;Lnet/minecraft/util/HttpUtil$DownloadProgressListener;)Ljava/nio/file/Path;"))
    private Path sre$sharedContentCache(Path directory, URL url, Map<String, String> headers,
                                       HashFunction hashFunction, HashCode expectedHash, int maxSize,
                                       Proxy proxy, HttpUtil.DownloadProgressListener listener, Operation<Path> original) {
        Path selected = directory;
        if (expectedHash != null && expectedHash.bits() == 160 && hashFunction.bits() == 160) {
            try {
                selected = ServerPackCache.prepareDirectory(directory, expectedHash.toString());
            } catch (IOException | SecurityException error) {
                // 缓存盘满、只读或被占用时，不让增强缓存阻止原版下载。
                sre$cacheLogger.warn("Shared resource-pack cache unavailable; using vanilla cache ({})",
                        error.getClass().getSimpleName());
            }
        }
        return original.call(selected, url, headers, hashFunction, expectedHash, maxSize, proxy, listener);
    }

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 20))
    private int sre$keepMoreServerPacks(int vanillaLimit) {
        // 仍使用原版最近使用文件清理；避免多服务器各自的包过早挤出 20 文件的小缓存。
        return 64;
    }
}

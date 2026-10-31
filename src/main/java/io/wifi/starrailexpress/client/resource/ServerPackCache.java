package io.wifi.starrailexpress.client.resource;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 将服务器资源包的下载目录按内容共享，而不是按服务器下发的 UUID 隔离。
 * 仅准备目录和迁移旧文件；命中校验、损坏重下、大小限制仍由原版下载器负责。
 * 此类只在原版 DownloadQueue 的后台下载任务内调用，不在渲染线程扫描磁盘。
 */
public final class ServerPackCache {
    private static final String SHARED_DIRECTORY = "sre-shared";

    private ServerPackCache() {
    }

    public static Path prepareDirectory(Path originalDirectory, String sha1) throws IOException {
        // 无有效内容哈希时不能跨请求复用 URL：同一 URL 的内容随时可能更新。
        if (sha1 == null || !sha1.matches("[0-9a-f]{40}")) {
            return originalDirectory;
        }
        Path root = originalDirectory.toAbsolutePath().normalize().getParent();
        if (root == null) {
            return originalDirectory;
        }
        Path shared = root.resolve(SHARED_DIRECTORY);
        Files.createDirectories(shared);
        Path target = shared.resolve(sha1);
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return shared;
        }
        // 优先使用此次 UUID 的旧缓存，再查其他原版 UUID 目录。不会访问资源包 ZIP 内部路径。
        if (migrate(originalDirectory.resolve(sha1), target)) {
            return shared;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!isPackDirectory(name) || !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (migrate(entry.resolve(sha1), target)) {
                    break;
                }
            }
        }
        return shared;
    }

    private static boolean isPackDirectory(String name) {
        try {
            return UUID.fromString(name).toString().equalsIgnoreCase(name);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean migrate(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        // 不移动旧文件：它可能还被当前服务器的资源管理器打开。
        Path temporary = Files.createTempFile(target.getParent(), "legacy-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, target);
            } catch (FileAlreadyExistsException ignored) {
                // 并发请求已经发布缓存，后续交给原版重新校验。
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return true;
    }
}

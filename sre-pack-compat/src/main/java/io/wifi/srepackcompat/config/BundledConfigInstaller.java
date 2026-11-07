package io.wifi.srepackcompat.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** 在任何客户端配置初始化前，将兼容包 config/ 资源覆盖到实例配置目录。 */
public final class BundledConfigInstaller {
    private BundledConfigInstaller() { }

    public static void install() {
        FabricLoader loader = FabricLoader.getInstance();
        var mod = loader.getModContainer("sre_pack_compat").orElseThrow();
        try {
            int changed = install(mod.getRootPaths(), loader.getConfigDir());
            LoggerFactory.getLogger(BundledConfigInstaller.class).info("已应用整合包内置配置，更新 {} 个文件", changed);
        } catch (IOException e) {
            throw new IllegalStateException("无法应用 SRE Pack Compat 内置配置，停止启动以避免使用未锁定设置", e);
        }
    }

    public static int install(List<Path> roots, Path configDir) throws IOException {
        int changed = 0;
        for (Path root : roots) {
            Path source = root.resolve("config");
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) continue;
            if (Files.isSymbolicLink(source) || !Files.isDirectory(source)) {
                throw new IOException("内置 config 不是普通目录: " + source);
            }
            try (var paths = Files.walk(source)) {
                for (Path file : paths.filter(p -> !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("内置配置不是普通文件: " + file);
                    }
                    // ZIP 文件系统的 Path 不能直接传给默认文件系统 resolve。
                    Path target = configDir.toAbsolutePath().normalize();
                    for (Path part : source.relativize(file)) target = target.resolve(part.toString());
                    target = target.normalize();
                    if (!target.startsWith(configDir.toAbsolutePath().normalize())) {
                        throw new IOException("配置目标越界: " + file);
                    }
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target)) {
                        throw new IOException("配置目标不是普通文件: " + target);
                    }
                    checkNoLinks(target);
                    Files.createDirectories(target.getParent());
                    if (Files.isRegularFile(target) && Files.mismatch(file, target) == -1) continue;
                    Path temp = Files.createTempFile(target.getParent(), ".sre-pack-compat-", ".tmp");
                    try {
                        Files.copy(file, temp, StandardCopyOption.REPLACE_EXISTING);
                        try {
                            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        } catch (AtomicMoveNotSupportedException e) {
                            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                        changed++;
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                }
            }
        }
        return changed;
    }

    private static void checkNoLinks(Path target) throws IOException {
        for (Path path = target; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("拒绝通过符号链接覆盖配置: " + path);
        }
    }
}

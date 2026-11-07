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
                    byte[] content = Files.readAllBytes(file);
                    if (source.relativize(file).toString().equals("chloride-client.toml") && Files.isRegularFile(target)) {
                        content = preserveFullscreenMode(content, Files.readString(target));
                    }
                    if (Files.isRegularFile(target) && java.util.Arrays.equals(content, Files.readAllBytes(target))) continue;
                    Path temp = Files.createTempFile(target.getParent(), ".sre-pack-compat-", ".tmp");
                    try {
                        Files.write(temp, content);
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

    /** 内置配置继续覆盖受限项，但不重置已开放的窗口模式。 */
    private static byte[] preserveFullscreenMode(byte[] bundled, String existing) {
        var section = java.util.regex.Pattern.compile("(?ms)(^\\[fullscreen\\][^\\r\\n]*\\R)(.*?)(?=^\\[|\\z)");
        var mode = java.util.regex.Pattern.compile("(?m)^\\h*mode\\h*=\\h*\"(WINDOWED|BORDERLESS|FULLSCREEN)\"\\h*(?:#[^\\r\\n]*)?$");
        var oldSection = section.matcher(existing);
        if (!oldSection.find()) return bundled;
        var oldMode = mode.matcher(oldSection.group(2));
        if (!oldMode.find()) return bundled;
        String text = new String(bundled, java.nio.charset.StandardCharsets.UTF_8);
        var newSection = section.matcher(text);
        if (!newSection.find()) return bundled;
        String body = mode.matcher(newSection.group(2)).replaceFirst("  mode = \"" + oldMode.group(1) + "\"");
        return (text.substring(0, newSection.start(2)) + body + text.substring(newSection.end(2)))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void checkNoLinks(Path target) throws IOException {
        for (Path path = target; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("拒绝通过符号链接覆盖配置: " + path);
        }
    }
}

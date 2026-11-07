package io.wifi.srepackcompat.startup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/** 普通配置文件控制启动缓存，不要求启动器参数或额外 agent。 */
record StartupCacheConfig(boolean enabled, int maxMiB, int maxEntries, int timeoutSeconds) {
    static final StartupCacheConfig DEFAULT = new StartupCacheConfig(true, 32, 8192, 180);
    private static final String TEMPLATE = """
            # 仅用于 Fabric 客户端 preLaunch 之后的本地 JAR 原始类字节缓存。
            # 不跳过 Mixin，不保存转换后字节，不跨进程复用缓存。
            enabled=true
            maxCacheMiB=32
            maxCacheEntries=8192
            timeoutSeconds=180
            """;

    static StartupCacheConfig load(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try {
            Files.writeString(file, TEMPLATE, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException ignored) {
            // 更新兼容包时保留用户的开关和预算。
        }
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        String enabled = values.getProperty("enabled", "true").trim();
        if (!enabled.equalsIgnoreCase("true") && !enabled.equalsIgnoreCase("false")) {
            throw new IOException("enabled must be true or false");
        }
        return new StartupCacheConfig(Boolean.parseBoolean(enabled),
                integer(values, "maxCacheMiB", 32, 1, 128),
                integer(values, "maxCacheEntries", 8192, 64, 32768),
                integer(values, "timeoutSeconds", 180, 10, 600));
    }

    private static int integer(Properties values, String key, int fallback, int min, int max) throws IOException {
        try {
            int value = Integer.parseInt(values.getProperty(key, Integer.toString(fallback)).trim());
            if (value < min || value > max) throw new NumberFormatException("out of range");
            return value;
        } catch (NumberFormatException error) {
            throw new IOException("Invalid startup cache option: " + key, error);
        }
    }
}

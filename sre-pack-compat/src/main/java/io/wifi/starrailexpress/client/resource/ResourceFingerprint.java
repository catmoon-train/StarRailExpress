package io.wifi.starrailexpress.client.resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** 重载缓存的有序指纹：文件内容仅在大小、时间或文件身份变化后重新读取。 */
public final class ResourceFingerprint {
    private record Stamp(long size, Object modified, Object created, Object key) { }
    private record Cached(Stamp stamp, String hash) { }
    private static final Map<Path, Cached> FILES = new LinkedHashMap<>(128, .75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, Cached> eldest) {
            return size() > 32768;
        }
    };

    private ResourceFingerprint() { }

    public static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static void text(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        // 长度前缀避免 ["a", "bc"] 与 ["ab", "c"] 产生相同输入。
        digest.update(new byte[]{(byte)(bytes.length >>> 24), (byte)(bytes.length >>> 16),
                (byte)(bytes.length >>> 8), (byte)bytes.length});
        digest.update(bytes);
    }

    public static String finish(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest());
    }

    public static void path(MessageDigest digest, Path path) throws IOException {
        if ("jar".equalsIgnoreCase(path.toUri().getScheme())) {
            // 指纹记录子目录，并复用同一 JAR 的内容哈希，避免为每个内置 overlay 重读 JAR。
            text(digest, path.toString());
            file(digest, Path.of(io.wifi.srepackcompat.PackArchiveFileUri.resolve(path.toUri(), path.toUri())));
        } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (var walk = Files.walk(path)) {
                for (Path entry : walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                        .sorted().toList()) {
                    text(digest, path.relativize(entry).toString().replace('\\', '/'));
                    file(digest, entry);
                }
            }
        } else {
            file(digest, path);
        }
    }

    private static void file(MessageDigest digest, Path file) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        BasicFileAttributes attrs = Files.readAttributes(normalized, BasicFileAttributes.class);
        Stamp stamp = new Stamp(attrs.size(), attrs.lastModifiedTime(), attrs.creationTime(), attrs.fileKey());
        Cached previous;
        synchronized (FILES) {
            previous = FILES.get(normalized);
        }
        String hash;
        if (previous != null && previous.stamp.equals(stamp)) {
            hash = previous.hash;
        } else {
            MessageDigest content = digest();
            try (InputStream input = Files.newInputStream(normalized)) {
                stream(content, input);
            }
            hash = finish(content);
            synchronized (FILES) {
                FILES.put(normalized, new Cached(stamp, hash));
            }
        }
        text(digest, hash);
    }

    public static void stream(MessageDigest digest, InputStream input) throws IOException {
        byte[] buffer = new byte[65536];
        int count;
        while ((count = input.read(buffer)) != -1) {
            digest.update(buffer, 0, count);
        }
    }

    public static void clear() {
        synchronized (FILES) {
            FILES.clear();
        }
    }
}

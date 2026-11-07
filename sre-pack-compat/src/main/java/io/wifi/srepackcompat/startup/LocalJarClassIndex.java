package io.wifi.srepackcompat.startup;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/**
 * JAR 清单仅筛选候选类；首次读取仍由原 URLClassLoader 决定资源来源。
 * 不自行猜测目录、清单 Class-Path、JAR 索引和 Multi-Release 的查找优先级。
 */
final class LocalJarClassIndex implements ClassBytesSource {
    private volatile Set<String> candidates;
    private final Function<String, URL> findResource;
    private final BooleanSupplier current;

    private LocalJarClassIndex(Set<String> candidates, Function<String, URL> findResource, BooleanSupplier current) {
        this.candidates = candidates;
        this.findResource = findResource;
        this.current = current;
    }

    static LocalJarClassIndex build(URL[] urls, Function<String, URL> findResource,
                                    BooleanSupplier current) throws IOException {
        if (urls.length == 0 || urls.length > 512) throw new IOException("Unsupported classpath size");
        Set<String> candidates = new HashSet<>();
        for (URL url : urls) {
            if (!url.getProtocol().equals("file")) continue;
            Path path;
            try {
                path = Path.of(url.toURI());
            } catch (Exception error) {
                throw new IOException("Invalid classpath entry", error);
            }
            // 不索引目录。目录与清单额外来源仍可遮挡 JAR，由原查找器决定。
            if (!Files.isRegularFile(path)) continue;
            try (JarFile jar = new JarFile(path.toFile(), true, ZipFile.OPEN_READ, Runtime.version());
                 var stream = jar.versionedStream()) {
                var iterator = stream.iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    String name = entry.getName();
                    if (!entry.isDirectory() && name.endsWith(".class") && !name.startsWith("META-INF/")) {
                        candidates.add(name);
                        if (candidates.size() > 131072) throw new IOException("Class index budget exceeded");
                    }
                }
            }
        }
        return new LocalJarClassIndex(candidates, findResource, current);
    }

    int size() {
        return candidates.size();
    }

    @Override
    public byte[] read(String className) throws IOException {
        String resourceName = className.replace('.', '/') + ".class";
        if (!candidates.contains(resourceName)) return null;
        URL resource = findResource.apply(resourceName);
        if (resource == null || !resource.getProtocol().equals("jar")) return null;
        if (!(resource.openConnection() instanceof JarURLConnection connection)
                || !connection.getJarFileURL().getProtocol().equals("file")) return null;
        // URL 已包含原加载器选定的 Multi-Release 版本路径；签名校验也仍由 JDK 完成。
        try (var input = connection.getInputStream()) {
            return input.readAllBytes();
        }
    }

    @Override
    public boolean isCurrent() {
        return current.getAsBoolean();
    }

    @Override
    public void close() {
        // 正在读取的线程可完成其快照；不并发修改它持有的 HashSet。
        candidates = Set.of();
    }
}

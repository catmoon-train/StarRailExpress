package io.wifi.srepackcompat.startup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

class LocalJarClassIndexTest {
    @TempDir Path directory;

    @AfterEach
    void closeOnlyFixtureJarConnections() throws Exception {
        // URL.openStream 与生产 Knot 一样使用 JDK 共享缓存；只清理本测试创建的文件。
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                var connection = (JarURLConnection) java.net.URI.create("jar:" + path.toUri() + "!/")
                        .toURL().openConnection();
                connection.getJarFile().close();
            }
        }
    }

    private Path jar(String name, Map<String, String> entries, Map<String, String> attributes) throws Exception {
        Path path = directory.resolve(name);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.forEach((key, value) -> manifest.getMainAttributes().putValue(key, value));
        try (var out = new JarOutputStream(Files.newOutputStream(path), manifest)) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return path;
    }

    private URL[] urls(Path... paths) throws Exception {
        URL[] result = new URL[paths.length];
        for (int i = 0; i < paths.length; i++) result[i] = paths[i].toUri().toURL();
        return result;
    }

    private byte[] original(URLClassLoader loader, String name) throws IOException {
        URL url = loader.findResource(name.replace('.', '/') + ".class");
        if (url == null) return null;
        try (var input = url.openStream()) { return input.readAllBytes(); }
    }

    @Test
    void duplicateClassesKeepOriginalUrlOrderAndSpecialCharacterPaths() throws Exception {
        Path first = jar("中文 [a]+%# space.jar", Map.of("test/Example.class", "first"), Map.of());
        Path second = jar("second.jar", Map.of("test/Example.class", "second"), Map.of());
        try (var loader = new URLClassLoader(urls(first, second), null)) {
            var index = LocalJarClassIndex.build(loader.getURLs(), loader::findResource, () -> true);
            assertArrayEquals(original(loader, "test.Example"), index.read("test.Example"));
            assertEquals("first", new String(index.read("test.Example"), StandardCharsets.UTF_8));
        }
    }

    @Test
    void multiReleaseUsesTheActualJdkSelectedEntry() throws Exception {
        Path path = jar("multi.jar", Map.of("test/Example.class", "base",
                "META-INF/versions/17/test/Example.class", "java17",
                "META-INF/versions/21/test/Example.class", "java21",
                "META-INF/versions/99/test/Example.class", "future"), Map.of("Multi-Release", "true"));
        try (var loader = new URLClassLoader(urls(path), null)) {
            var index = LocalJarClassIndex.build(loader.getURLs(), loader::findResource, () -> true);
            assertArrayEquals(original(loader, "test.Example"), index.read("test.Example"));
            assertEquals("java21", new String(index.read("test.Example"), StandardCharsets.UTF_8));
        }
    }

    @Test
    void manifestDependencyCanShadowAnExplicitLaterJar() throws Exception {
        jar("dependency.jar", Map.of("test/Example.class", "dependency"), Map.of());
        Path parent = jar("parent.jar", Map.of("other/Present.class", "parent"), Map.of("Class-Path", "dependency.jar"));
        Path later = jar("later.jar", Map.of("test/Example.class", "later"), Map.of());
        try (var loader = new URLClassLoader(urls(parent, later), null)) {
            var index = LocalJarClassIndex.build(loader.getURLs(), loader::findResource, () -> true);
            assertArrayEquals(original(loader, "test.Example"), index.read("test.Example"));
            assertEquals("dependency", new String(index.read("test.Example"), StandardCharsets.UTF_8));
        }
    }

    @Test
    void directoryShadowAndDirectoryOnlyClassesStayOnOriginalPath() throws Exception {
        Path classes = directory.resolve("classes");
        Files.createDirectories(classes.resolve("test"));
        Files.writeString(classes.resolve("test/Example.class"), "directory", StandardCharsets.UTF_8);
        Path jar = jar("later.jar", Map.of("test/Example.class", "jar"), Map.of());
        try (var loader = new URLClassLoader(urls(classes, jar), null)) {
            var index = LocalJarClassIndex.build(loader.getURLs(), loader::findResource, () -> true);
            assertNull(index.read("test.Example"));
            assertEquals("directory", new String(original(loader, "test.Example"), StandardCharsets.UTF_8));
            Files.writeString(classes.resolve("test/New.class"), "new", StandardCharsets.UTF_8);
            assertNull(index.read("test.New"));
            assertEquals("new", new String(original(loader, "test.New"), StandardCharsets.UTF_8));
        }
    }

    @Test
    void noCandidateDoesNotDoubleScanParentOnlyClasses() throws Exception {
        Path jar = jar("one.jar", Map.of("test/Example.class", "jar"), Map.of());
        AtomicInteger calls = new AtomicInteger();
        try (var loader = new URLClassLoader(urls(jar), null)) {
            var index = LocalJarClassIndex.build(loader.getURLs(), name -> {
                calls.incrementAndGet(); return loader.findResource(name);
            }, () -> true);
            assertNull(index.read("java.lang.String"));
            assertNull(index.read("missing.Class"));
            assertEquals(0, calls.get());
            index.read("test.Example");
            assertEquals(1, calls.get());
            index.close();
            assertEquals(0, index.size());
            assertNull(index.read("test.Example"));
        }
    }

    @Test
    void candidateWithMissingOrRemoteActualResourceIsNotCached() throws Exception {
        Path jar = jar("one.jar", Map.of("test/Example.class", "jar"), Map.of());
        URL[] urls = urls(jar);
        var missing = LocalJarClassIndex.build(urls, name -> null, () -> true);
        assertNull(missing.read("test.Example"));
        URL remote = new URL("https://example.invalid/Example.class");
        var nonLocal = LocalJarClassIndex.build(urls, name -> remote, () -> true);
        assertNull(nonLocal.read("test.Example"));
    }
}

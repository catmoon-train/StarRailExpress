package io.wifi.srepackcompat.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BundledConfigInstallerTest {
    @TempDir Path temp;

    @Test void nestedResourcesOverwriteMatchingFilesButKeepOtherConfigs() throws Exception {
        Path root = temp.resolve("mod");
        Path target = temp.resolve("game/config");
        Files.createDirectories(root.resolve("config/nested"));
        Files.writeString(root.resolve("config/chloride-client.toml"), "enabled=true");
        Files.writeString(root.resolve("config/nested/中文.json"), "{}");
        Files.createDirectories(target);
        Files.writeString(target.resolve("chloride-client.toml"), "enabled=false");
        Files.writeString(target.resolve("unrelated.json"), "keep");
        assertEquals(2, BundledConfigInstaller.install(List.of(root), target));
        assertEquals("enabled=true", Files.readString(target.resolve("chloride-client.toml")));
        assertEquals("{}", Files.readString(target.resolve("nested/中文.json")));
        assertEquals("keep", Files.readString(target.resolve("unrelated.json")));
    }

    @Test void everyLaunchRestoresEditsButDoesNotRewriteIdenticalContent() throws Exception {
        Path root = temp.resolve("mod");
        Path target = temp.resolve("game/config");
        Files.createDirectories(root.resolve("config"));
        Files.writeString(root.resolve("config/test.toml"), "value=1");
        assertEquals(1, BundledConfigInstaller.install(List.of(root), target));
        Path file = target.resolve("test.toml");
        Files.setLastModifiedTime(file, FileTime.fromMillis(10000));
        assertEquals(0, BundledConfigInstaller.install(List.of(root), target));
        assertEquals(10000, Files.getLastModifiedTime(file).toMillis());
        Files.writeString(file, "value=2");
        assertEquals(1, BundledConfigInstaller.install(List.of(root), target));
        assertEquals("value=1", Files.readString(file));
        Files.writeString(root.resolve("config/test.toml"), "value=3");
        assertEquals(1, BundledConfigInstaller.install(List.of(root), target));
        assertEquals("value=3", Files.readString(file));
    }

    @Test void readsConfigTreeInsideActualJarFileSystem() throws Exception {
        Path jar = temp.resolve("compat.jar");
        try (var zip = FileSystems.newFileSystem(java.net.URI.create("jar:" + jar.toUri()), Map.of("create", "true"))) {
            Files.createDirectories(zip.getPath("/config/nested"));
            Files.writeString(zip.getPath("/config/nested/pack.toml"), "value=42");
        }
        try (var zip = FileSystems.newFileSystem(jar)) {
            Path target = temp.resolve("game/config");
            assertEquals(1, BundledConfigInstaller.install(List.of(zip.getPath("/")), target));
            assertEquals("value=42", Files.readString(target.resolve("nested/pack.toml")));
        }
    }

    @Test void missingConfigResourceDoesNotTouchInstance() throws Exception {
        Path root = Files.createDirectory(temp.resolve("mod"));
        Path target = temp.resolve("game/config");
        assertEquals(0, BundledConfigInstaller.install(List.of(root), target));
        assertFalse(Files.exists(target));
    }

    @Test void refusesReplacingDirectoryWithAConfigFile() throws Exception {
        Path root = temp.resolve("mod");
        Files.createDirectories(root.resolve("config"));
        Files.writeString(root.resolve("config/a.toml"), "value=1");
        Path target = temp.resolve("game/config");
        Files.createDirectories(target.resolve("a.toml"));
        Files.writeString(target.resolve("a.toml/keep.txt"), "keep");
        assertThrows(java.io.IOException.class, () -> BundledConfigInstaller.install(List.of(root), target));
        assertEquals("keep", Files.readString(target.resolve("a.toml/keep.txt")));
    }
}

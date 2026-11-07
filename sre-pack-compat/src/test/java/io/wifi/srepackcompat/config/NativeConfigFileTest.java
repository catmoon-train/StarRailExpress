package io.wifi.srepackcompat.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NativeConfigFileTest {
    @TempDir Path temp;

    @Test void jarResourceBecomesToFileCompatibleAndTemporaryCopyIsDeleted() throws Exception {
        Path jar = temp.resolve("compat.jar");
        try (var zip = FileSystems.newFileSystem(java.net.URI.create("jar:" + jar.toUri()), Map.of("create", "true"))) {
            Files.createDirectories(zip.getPath("/config"));
            Files.writeString(zip.getPath("/config/chloride-client.toml"), "[fog]\nenabled=true\n# 中文\n");
        }
        Path staged;
        try (var zip = FileSystems.newFileSystem(jar)) {
            Path resource = zip.getPath("/config/chloride-client.toml");
            assertThrows(UnsupportedOperationException.class, resource::toFile);
            byte[] original = Files.readAllBytes(resource);
            try (var file = NativeConfigFile.copyOf(resource)) {
                staged = file.path();
                // 模拟实际 WaterConfig 的 FileInputStream / Path.toFile 路径，而非 Files.readString。
                try (var stream = new FileInputStream(file.path().toFile())) {
                    assertArrayEquals(original, stream.readAllBytes());
                }
            }
            assertFalse(Files.exists(staged));
            assertArrayEquals(original, Files.readAllBytes(resource));
        }
    }

    @Test void nativeResourceIsCopiedNotHandedToParserForModification() throws Exception {
        Path source = temp.resolve("source.toml");
        Files.writeString(source, "enabled=true", StandardCharsets.UTF_8);
        Path staged;
        try (var file = NativeConfigFile.copyOf(source)) {
            staged = file.path();
            assertNotEquals(source, staged);
            Files.writeString(staged, "enabled=false");
        }
        assertFalse(Files.exists(staged));
        assertEquals("enabled=true", Files.readString(source));
    }

    @Test void parserFailureStillDeletesStagedFileAndPreservesFailure() throws Exception {
        Path source = temp.resolve("bad.toml");
        Files.writeString(source, "invalid");
        var staged = new AtomicReference<Path>();
        IOException expected = new IOException("parse failed");
        IOException actual = assertThrows(IOException.class, () -> {
            try (var file = NativeConfigFile.copyOf(source)) {
                staged.set(file.path());
                throw expected;
            }
        });
        assertSame(expected, actual);
        assertFalse(Files.exists(staged.get()));
    }

    @Test void missingSourceFailsInsteadOfReadingInstanceConfig() {
        assertThrows(IOException.class, () -> NativeConfigFile.copyOf(temp.resolve("missing.toml")));
    }
}

package io.wifi.starrailexpress.client.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveFileUriTest {
    @TempDir
    Path root;

    @ParameterizedTest
    @ValueSource(strings = {"mod.jar", "[现代化 UI] mod.jar", "mod+plus.jar", "mod%20literal.jar", "mod#hash.jar"})
    void zipRootResolvesToExactArchiveWithoutDoubleDecoding(String name) throws Exception {
        Path archive = root.resolve(name).toAbsolutePath();
        try (var zip = FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
            URI packRoot = zip.getPath("/").toUri();
            URI result = ArchiveFileUri.resolve(packRoot, URI.create("file:///wrong.jar"));
            assertEquals(archive, Path.of(result));
            assertTrue(Files.isRegularFile(Path.of(result)));
        }
    }

    @Test
    void nonArchiveAndUnsupportedSchemesKeepOriginalBehavior() {
        URI fallback = URI.create("file:///fallback.jar");
        assertSame(fallback, ArchiveFileUri.resolve(root.toUri(), fallback));
        assertSame(fallback, ArchiveFileUri.resolve(URI.create("jar:https://example.invalid/mod.jar!/"), fallback));
        assertSame(fallback, ArchiveFileUri.resolve(URI.create("jar:file:///mod.jar"), fallback));
    }
}

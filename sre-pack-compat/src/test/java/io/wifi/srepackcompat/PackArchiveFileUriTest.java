package io.wifi.srepackcompat;

import io.wifi.starrailexpress.mixin.compat.lightspeed.ArchiveFileUriMixin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PackArchiveFileUriTest {
    @TempDir Path root;

    @ParameterizedTest
    @ValueSource(strings = {"mod.jar", "[进食动画] eating-animation-1.21_1.9.72.jar",
            "中文+plus.jar", "中文%20literal.jar", "中文#hash.jar", "中文%25percent.jar"})
    void archivePathRoundTripsOnUnixWithoutDoubleDecoding(String name) throws Exception {
        Path archive = root.resolve(name).toAbsolutePath();
        try (var zip = FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
            Path pack = zip.getPath("/resourcepacks/supporteatinganimation");
            Files.createDirectories(pack);
            Files.writeString(pack.resolve("pack.mcmeta"), "{}");
            URI resolved = PackArchiveFileUri.resolve(pack.toUri(), URI.create("file:///wrong.jar"));
            assertEquals(archive, Path.of(resolved));
            assertEquals(archive.toUri().getPath(), unixDecode(resolved));
            assertTrue(Files.isRegularFile(Path.of(resolved)));
            assertTrue(resolved.getRawPath().chars().allMatch(c -> c < 0x80));
            assertSame(resolved, PackArchiveFileUri.normalizeFileUri(resolved));
        }
    }

    @Test void originalCoreResultFailsUnixAndRegisteredCallbackRepairsIt() throws Exception {
        URI original = URI.create("file:///mods/%5B进食动画%5D%20eating+literal%2520.jar");
        assertEquals("Bad escape", assertThrows(IllegalArgumentException.class, () -> unixDecode(original)).getMessage());
        var callback = new CallbackInfoReturnable<URI>("resolve", true, original);
        var method = ArchiveFileUriMixin.class.getDeclaredMethod("srepack$asciiFileUri", URI.class, URI.class, CallbackInfoReturnable.class);
        method.setAccessible(true);
        method.invoke(null, URI.create("jar:" + original + "!/resourcepacks/supporteatinganimation"), original, callback);
        assertEquals("/mods/[进食动画] eating+literal%20.jar", unixDecode(callback.getReturnValue()));
        assertTrue(callback.isCancelled());
    }

    @Test void validUrisAndUnsupportedFallbacksKeepOriginalIdentity() {
        URI ascii = URI.create("file:///mods/%E4%B8%AD%E6%96%87%20mod%2520+plus.jar");
        URI https = URI.create("https://example.invalid/中文.jar");
        assertSame(ascii, PackArchiveFileUri.normalizeFileUri(ascii));
        assertSame(https, PackArchiveFileUri.normalizeFileUri(https));
        assertNull(PackArchiveFileUri.normalizeFileUri(null));
        assertSame(ascii, PackArchiveFileUri.resolve(URI.create("jar:https://example.invalid/mod.jar!/"), ascii));
        assertSame(ascii, PackArchiveFileUri.resolve(URI.create("jar:file:///mod.jar"), ascii));
        assertSame(ascii, PackArchiveFileUri.resolve(root.toUri(), ascii));
    }

    // OpenJDK 21 UnixUriUtils raw-path decoding contract, portable to the Windows build host.
    private static String unixDecode(URI uri) {
        String raw = uri.getRawPath();
        var bytes = new ByteArrayOutputStream();
        for (int pos = 0; pos < raw.length();) {
            char c = raw.charAt(pos++);
            int b;
            if (c == '%') {
                b = (Character.digit(raw.charAt(pos++), 16) << 4) | Character.digit(raw.charAt(pos++), 16);
                if (b == 0) throw new IllegalArgumentException("Nul character not allowed");
            } else {
                if (c == 0 || c >= 0x80) throw new IllegalArgumentException("Bad escape");
                b = c;
            }
            bytes.write(b);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}

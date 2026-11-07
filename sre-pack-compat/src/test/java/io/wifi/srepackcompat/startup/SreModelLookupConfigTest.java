package io.wifi.srepackcompat.startup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SreModelLookupConfigTest {
    @TempDir Path directory;

    @Test
    void absentConfigDefaultsOnWithoutWritingAnything() throws Exception {
        Path path = directory.resolve("absent/models.properties");
        assertTrue(SreModelLookupConfig.enabled(path));
        assertFalse(Files.exists(path.getParent()));
    }

    @Test
    void explicitOffIsPreservedAndMalformedOrUnreadableConfigFails() throws Exception {
        Path path = directory.resolve("models.properties");
        Files.writeString(path, "# 用户设置\nenabled= FALSE \n", StandardCharsets.UTF_8);
        byte[] original = Files.readAllBytes(path);
        assertFalse(SreModelLookupConfig.enabled(path));
        assertArrayEquals(original, Files.readAllBytes(path));
        Files.writeString(path, "enabled=true", StandardCharsets.UTF_8);
        assertTrue(SreModelLookupConfig.enabled(path));
        Files.writeString(path, "enabled=unknown", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> SreModelLookupConfig.enabled(path));
        assertThrows(IOException.class, () -> SreModelLookupConfig.enabled(directory));
    }

    @Test
    void unknownCoreBytesNeverEnableLambdaSpecificPatch() {
        assertFalse(SreModelLookupConfig.matchesKnownCore(new byte[0]));
        assertFalse(SreModelLookupConfig.matchesKnownCore(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}));
        assertFalse(SreModelLookupConfig.matchesKnownCore("different implementation".getBytes(StandardCharsets.UTF_8)));
    }
}

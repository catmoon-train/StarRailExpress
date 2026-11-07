package io.wifi.srepackcompat.startup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StartupCacheConfigTest {
    @TempDir Path directory;

    @Test
    void createsUtf8DefaultsWithoutLauncherFlags() throws Exception {
        Path file = directory.resolve("config/startup.properties");
        assertEquals(StartupCacheConfig.DEFAULT, StartupCacheConfig.load(file));
        byte[] data = Files.readAllBytes(file);
        assertFalse(data[0] == (byte) 0xEF && data[1] == (byte) 0xBB);
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("不跳过 Mixin"));
    }

    @Test
    void preservesExistingDisabledConfigOnUpgrade() throws Exception {
        Path file = directory.resolve("startup.properties");
        String text = "# 用户设置\nenabled=false\nmaxCacheMiB=8\nmaxCacheEntries=128\ntimeoutSeconds=30\n";
        Files.writeString(file, text, StandardCharsets.UTF_8);
        assertEquals(new StartupCacheConfig(false, 8, 128, 30), StartupCacheConfig.load(file));
        assertEquals(text, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void rejectsUnboundedAndMalformedOptions() throws Exception {
        Path file = directory.resolve("startup.properties");
        for (String option : new String[]{"enabled=perhaps", "maxCacheMiB=4096", "maxCacheEntries=0",
                "timeoutSeconds=-1", "maxCacheMiB=NaN"}) {
            Files.writeString(file, option, StandardCharsets.UTF_8);
            assertThrows(IOException.class, () -> StartupCacheConfig.load(file), option);
        }
    }
}

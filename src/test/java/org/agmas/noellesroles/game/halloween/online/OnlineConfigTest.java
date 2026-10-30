package org.agmas.noellesroles.game.halloween.online;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class OnlineConfigTest {
    @TempDir Path temp;

    @Test void defaultUsesSharedMysqlPrefixAndDoesNotContainHttpConfiguration() throws Exception {
        Path path = temp.resolve("config/halloween-online.json");
        OnlineConfig config = OnlineConfig.load(path);
        assertFalse(config.enabled); assertEquals("sre_halloween_earnings", config.tableName("sre_"));
        assertEquals("test_halloween_earnings", config.tableName("test_"));
        assertEquals(128, config.batchSize); assertEquals(30, config.flushIntervalSeconds);
        String text = Files.readString(path);
        assertFalse(text.contains("baseUrl")); assertFalse(text.contains("bridgeSecret")); assertFalse(text.contains("password"));
    }

    @Test void legacyEnabledConfigIsReadWithoutUrlSecretAndKeepsBatchSettings() throws Exception {
        Path path = temp.resolve("legacy.json");
        Files.writeString(path, "{\"enabled\":true,\"baseUrl\":\"https://unused.invalid\",\"bridgeSecret\":\"obsolete\",\"batchSize\":64,\"flushIntervalSeconds\":45,\"requestTimeoutSeconds\":3}");
        OnlineConfig config = OnlineConfig.load(path);
        assertTrue(config.enabled); config.validate(); assertEquals(64, config.batchSize); assertEquals(45, config.flushIntervalSeconds);
        assertEquals(10, config.statementTimeoutSeconds); assertEquals("sre_halloween_earnings", config.tableName("sre_"));
    }

    @Test void identifiersAndLimitsAreValidatedBeforeUsingSql() {
        OnlineConfig config = new OnlineConfig();
        for (String bad : new String[]{"x`;DROP TABLE x", "db.table", "a b", "x-1", "a".repeat(65)}) {
            config.earningsTable = bad; assertThrows(IllegalArgumentException.class, config::validate);
        }
        config.earningsTable = "custom_earnings"; assertEquals("custom_earnings", config.tableName("ignored_"));
        config.batchSize = 129; assertThrows(IllegalArgumentException.class, config::validate);
        config.batchSize = 0; assertThrows(IllegalArgumentException.class, config::validate);
        config.batchSize = 128; config.statementTimeoutSeconds = 0; assertThrows(IllegalArgumentException.class, config::validate);
        config.statementTimeoutSeconds = 10; config.flushIntervalSeconds = -1; assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test void invalidConfigDoesNotLeakOldCredentials() throws Exception {
        Path path = temp.resolve("bad.json"); Files.writeString(path, "{obsolete-secret");
        Exception failure = assertThrows(java.io.IOException.class, () -> OnlineConfig.load(path));
        assertFalse(failure.toString().contains("obsolete-secret"));
    }
}

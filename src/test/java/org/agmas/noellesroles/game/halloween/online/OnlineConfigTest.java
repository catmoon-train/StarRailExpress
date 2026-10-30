package org.agmas.noellesroles.game.halloween.online;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OnlineConfigTest {
    @TempDir Path temp;

    @Test void snapshotUsesSharedMysqlPrefixAndCopiesAllSixSettings() {
        OnlineConfig defaults = new OnlineConfig();
        OnlineConfig config = OnlineConfig.fromSettings(defaults.enabled, defaults.earningsTable,
                defaults.createTableIfMissing, defaults.flushIntervalSeconds, defaults.batchSize,
                defaults.statementTimeoutSeconds);
        assertFalse(config.enabled);
        assertEquals("sre_halloween_earnings", config.tableName("sre_"));
        assertEquals("test_halloween_earnings", config.tableName("test_"));
        assertTrue(config.createTableIfMissing);
        assertEquals(128, config.batchSize);
        assertEquals(30, config.flushIntervalSeconds);
        assertEquals(10, config.statementTimeoutSeconds);
        OnlineConfig custom = OnlineConfig.fromSettings(true, "custom_earnings", false, 45, 64, 5);
        assertTrue(custom.enabled);
        assertEquals("custom_earnings", custom.tableName("ignored_"));
        assertFalse(custom.createTableIfMissing);
        assertEquals(45, custom.flushIntervalSeconds);
        assertEquals(64, custom.batchSize);
        assertEquals(5, custom.statementTimeoutSeconds);
        defaults.batchSize = 1;
        assertEquals(128, config.batchSize);
        assertThrows(IllegalArgumentException.class,
                () -> OnlineConfig.fromSettings(true, "", true, 0, 128, 10));
    }

    @Test void missingLegacyDoesNotCreateAnyConfiguration() throws Exception {
        Path main = temp.resolve("starrailexpress.json"), legacy = temp.resolve("halloween-online.json");
        assertFalse(OnlineConfigMigration.migrate(main, legacy));
        assertFalse(Files.exists(main));
        assertFalse(Files.exists(legacy));
        Files.writeString(main, "{\"mysqlSyncPassword\":\"keep\",\"unknown\":42}\n");
        byte[] before = Files.readAllBytes(main);
        assertFalse(OnlineConfigMigration.migrate(main, legacy));
        assertArrayEquals(before, Files.readAllBytes(main));
        assertFalse(Files.exists(legacy));
    }

    @Test void migratesSixSettingsPreservingCredentialsMasterSwitchUnknownDataAndWal() throws Exception {
        Path main = temp.resolve("starrailexpress.json"), legacy = temp.resolve("halloween-online.json");
        String existing = """
                {"mysqlPlayerSyncEnabled":false,"mysqlSyncPassword":"keep-password<&>",
                 "mysqlSyncHost":"db.internal","mysqlSyncTablePrefix":"existing_",
                 "unknown":{"array":[1,null,"中文"],"future":true},"futureNull":null}
                """;
        Files.writeString(main, existing);
        String old = """
                {"enabled":true,"earningsTable":"custom_earnings","createTableIfMissing":false,
                 "flushIntervalSeconds":45,"batchSize":64,"statementTimeoutSeconds":5,
                 "baseUrl":"https://unused.invalid","bridgeSecret":"obsolete-secret","requestTimeoutSeconds":3}
                """;
        Files.writeString(legacy, old);
        Path wal = wal(); byte[] walBefore = Files.readAllBytes(wal);
        assertTrue(OnlineConfigMigration.migrate(main, legacy));
        JsonObject migrated = JsonParser.parseString(Files.readString(main)).getAsJsonObject();
        JsonObject original = JsonParser.parseString(existing).getAsJsonObject();
        original.entrySet().forEach(entry -> assertEquals(entry.getValue(), migrated.get(entry.getKey())));
        assertEquals(original.size() + 6, migrated.size());
        assertTrue(migrated.get("mysqlHalloweenSyncEnabled").getAsBoolean());
        assertEquals("custom_earnings", migrated.get("mysqlHalloweenEarningsTable").getAsString());
        assertFalse(migrated.get("mysqlHalloweenCreateTableIfMissing").getAsBoolean());
        assertEquals(45, migrated.get("mysqlHalloweenFlushIntervalSeconds").getAsInt());
        assertEquals(64, migrated.get("mysqlHalloweenBatchSize").getAsInt());
        assertEquals(5, migrated.get("mysqlHalloweenStatementTimeoutSeconds").getAsInt());
        assertFalse(Files.readString(main).contains("obsolete-secret"));
        assertEquals(old, Files.readString(legacy));
        assertArrayEquals(walBefore, Files.readAllBytes(wal));
        assertNoTemporaryFiles();
        byte[] after = Files.readAllBytes(main);
        Files.writeString(legacy, "{obsolete-secret:broken");
        assertFalse(OnlineConfigMigration.migrate(main, legacy)); // 重启后连损坏旧文件也不再读取。
        assertArrayEquals(after, Files.readAllBytes(main));
    }

    @Test void anyNewFieldIncludingExplicitFalseWinsOverLegacyTrue() throws Exception {
        Path main = temp.resolve("starrailexpress.json"), legacy = temp.resolve("halloween-online.json");
        Files.writeString(legacy, "{\"enabled\":true}");
        for (String existing : new String[]{"{\"mysqlHalloweenSyncEnabled\":false}",
                "{\"mysqlHalloweenBatchSize\":32}", "{\"mysqlHalloweenEarningsTable\":null}"}) {
            Files.writeString(main, existing);
            assertFalse(OnlineConfigMigration.migrate(main, legacy));
            assertEquals(existing, Files.readString(main));
        }
        Files.writeString(legacy, "not-json-obsolete-secret");
        assertFalse(OnlineConfigMigration.migrate(main, legacy));
    }

    @Test void missingLegacyFieldsUseDefaultsAndMissingMainDoesNotEnableMysqlMaster() throws Exception {
        Path main = temp.resolve("starrailexpress.json"), legacy = temp.resolve("halloween-online.json");
        Files.writeString(legacy, "{\"enabled\":true,\"bridgeSecret\":\"ignored\"}");
        assertTrue(OnlineConfigMigration.migrate(main, legacy));
        JsonObject migrated = JsonParser.parseString(Files.readString(main)).getAsJsonObject();
        assertEquals(6, migrated.size());
        assertFalse(migrated.has("mysqlPlayerSyncEnabled"));
        assertTrue(migrated.get("mysqlHalloweenSyncEnabled").getAsBoolean());
        assertEquals("", migrated.get("mysqlHalloweenEarningsTable").getAsString());
        assertTrue(migrated.get("mysqlHalloweenCreateTableIfMissing").getAsBoolean());
        assertEquals(30, migrated.get("mysqlHalloweenFlushIntervalSeconds").getAsInt());
        assertEquals(128, migrated.get("mysqlHalloweenBatchSize").getAsInt());
        assertEquals(10, migrated.get("mysqlHalloweenStatementTimeoutSeconds").getAsInt());
    }

    @Test void invalidLegacyFailsClosedWithoutTouchingOriginalsOrWalOrLeakingSecrets() throws Exception {
        Path main = temp.resolve("starrailexpress.json"), legacy = temp.resolve("halloween-online.json");
        Files.writeString(main, "{\"mysqlSyncPassword\":\"keep-password\",\"unknown\":true}");
        byte[] mainBefore = Files.readAllBytes(main);
        Path wal = wal(); byte[] walBefore = Files.readAllBytes(wal);
        for (String invalid : new String[]{"{obsolete-secret", "null", "[]", "{} trailing",
                "{\"enabled\":\"true\"}", "{\"enabled\":null}", "{\"earningsTable\":null}",
                "{\"earningsTable\":\"bad.table\"}", "{\"createTableIfMissing\":1}",
                "{\"batchSize\":0}", "{\"batchSize\":129}", "{\"batchSize\":1.5}",
                "{\"batchSize\":\"64\"}", "{\"batchSize\":4294967360}",
                "{\"flushIntervalSeconds\":86401}", "{\"statementTimeoutSeconds\":61}"}) {
            Files.writeString(legacy, invalid);
            IOException failure = assertThrows(IOException.class, () -> OnlineConfigMigration.migrate(main, legacy));
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains("obsolete-secret"));
            assertFalse(failure.toString().contains("keep-password"));
            assertArrayEquals(mainBefore, Files.readAllBytes(main));
            assertEquals(invalid, Files.readString(legacy));
            assertArrayEquals(walBefore, Files.readAllBytes(wal));
            assertNoTemporaryFiles();
        }
    }

    @Test void damagedMainIsNotReplacedByValidLegacy() throws Exception {
        Path main = temp.resolve("starrailexpress.json"), legacy = temp.resolve("halloween-online.json");
        Files.writeString(main, "{keep-password:broken");
        Files.writeString(legacy, "{\"enabled\":true}");
        assertThrows(IOException.class, () -> OnlineConfigMigration.migrate(main, legacy));
        assertEquals("{keep-password:broken", Files.readString(main));
        assertNoTemporaryFiles();
    }

    @Test void atomicReplaceFailureRetainsOriginalAndWalAndCanRetry() throws Exception {
        Path main = temp.resolve("starrailexpress.json"), legacy = temp.resolve("halloween-online.json");
        Files.writeString(main, "{\"mysqlSyncPassword\":\"keep-password\"}");
        Files.writeString(legacy, "{\"enabled\":true}");
        byte[] before = Files.readAllBytes(main), old = Files.readAllBytes(legacy);
        Path wal = wal(); byte[] walBefore = Files.readAllBytes(wal);
        IOException failure = assertThrows(IOException.class, () -> OnlineConfigMigration.migrate(main, legacy,
                (source, target) -> {
                    assertEquals(main.toAbsolutePath().getParent(), source.getParent());
                    assertTrue(Files.size(source) > 0);
                    throw new IOException("keep-password: injected disk failure");
                }));
        assertFalse(failure.toString().contains("keep-password"));
        assertNull(failure.getCause());
        assertArrayEquals(before, Files.readAllBytes(main));
        assertArrayEquals(old, Files.readAllBytes(legacy));
        assertArrayEquals(walBefore, Files.readAllBytes(wal));
        assertNoTemporaryFiles();
        assertTrue(OnlineConfigMigration.migrate(main, legacy));
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
        config.flushIntervalSeconds = 30; config.earningsTable = "";
        assertThrows(IllegalArgumentException.class, () -> config.tableName("bad.prefix"));
    }

    private Path wal() throws IOException {
        Path wal = temp.resolve("world/halloween-online/earnings.wal");
        Files.createDirectories(wal.getParent());
        Files.writeString(wal, "existing-stable-world-and-earnings-ids\n");
        return wal;
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (var paths = Files.list(temp)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}

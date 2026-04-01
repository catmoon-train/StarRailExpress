package io.wifi.starrailexpress.sync;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.wifi.starrailexpress.SREConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class MysqlPlayerDataStore {
    private static final Logger logger = LoggerFactory.getLogger(MysqlPlayerDataStore.class);
    private static final Pattern TABLE_PREFIX_PATTERN = Pattern.compile("[A-Za-z0-9_]*");
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private int index = 1;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "sre-mysql-sync-" + index++);
            thread.setDaemon(true);
            return thread;
        }
    });

    private static volatile HikariDataSource dataSource;
    private static volatile String tableName = "sre_player_sync_data";

    private MysqlPlayerDataStore() {
    }

    public record SyncRecord(String payload, long updatedAt) {
    }

    public static synchronized void initializeFromConfig() {
        shutdownDataSource();
        if (!SREConfig.instance().mysqlPlayerSyncEnabled) {
            logger.info("MySQL 玩家数据同步未启用，跳过数据库初始化。");
            return;
        }

        tableName = sanitizeTablePrefix(SREConfig.instance().mysqlSyncTablePrefix) + "player_sync_data";

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(buildJdbcUrl());
        hikariConfig.setUsername(SREConfig.instance().mysqlSyncUsername);
        hikariConfig.setPassword(SREConfig.instance().mysqlSyncPassword);
        hikariConfig.setPoolName("SRE-MySQLSync");
        hikariConfig.setMaximumPoolSize(Math.max(2, SREConfig.instance().mysqlSyncPoolSize));
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(Math.max(2000, SREConfig.instance().mysqlSyncConnectTimeoutMs));
        hikariConfig.setValidationTimeout(Math.min(hikariConfig.getConnectionTimeout(), 2000));
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");

        HikariDataSource candidate = null;
        try {
            candidate = new HikariDataSource(hikariConfig);
            try (Connection connection = candidate.getConnection()) {
                ensureSchema(connection);
            }
            dataSource = candidate;
            logger.info("MySQL 玩家数据同步已连接到 {}:{}/{}，表 {} 已就绪。",
                    SREConfig.instance().mysqlSyncHost,
                    SREConfig.instance().mysqlSyncPort,
                    SREConfig.instance().mysqlSyncDatabase,
                    tableName);
        } catch (SQLException exception) {
            if (candidate != null) {
                candidate.close();
            }
            logger.error("初始化 MySQL 玩家数据同步失败。", exception);
        }
    }

    public static synchronized void shutdown() {
        shutdownDataSource();
    }

    public static boolean isAvailable() {
        return dataSource != null;
    }

    public static CompletableFuture<Map<String, SyncRecord>> loadBatchAsync(UUID playerUuid, Collection<String> dataKeys) {
        List<String> normalizedKeys = normalizeKeys(dataKeys);
        if (playerUuid == null || normalizedKeys.isEmpty() || dataSource == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return CompletableFuture.supplyAsync(() -> loadBatch(playerUuid, normalizedKeys), EXECUTOR);
    }

    public static CompletableFuture<Boolean> saveBatchAsync(UUID playerUuid, Map<String, String> payloads, long updatedAt) {
        Map<String, String> normalizedPayloads = normalizePayloads(payloads);
        if (playerUuid == null || normalizedPayloads.isEmpty() || dataSource == null) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> saveBatch(playerUuid, normalizedPayloads, updatedAt), EXECUTOR);
    }

    public static boolean saveBatchBlocking(UUID playerUuid, Map<String, String> payloads, long updatedAt, long timeoutMs) {
        try {
            return saveBatchAsync(playerUuid, payloads, updatedAt).get(Math.max(1000L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            logger.warn("等待 MySQL 数据同步完成时失败，玩家 {}。", playerUuid, exception);
            return false;
        }
    }

    private static Map<String, SyncRecord> loadBatch(UUID playerUuid, List<String> dataKeys) {
        HikariDataSource source = dataSource;
        if (source == null) {
            return Map.of();
        }

        String placeholders = String.join(",", Collections.nCopies(dataKeys.size(), "?"));
        String sql = "SELECT data_key, payload_json, updated_at FROM " + tableName
                + " WHERE player_uuid = ? AND data_key IN (" + placeholders + ")";
        Map<String, SyncRecord> records = new LinkedHashMap<>();
        try (Connection connection = source.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            for (int index = 0; index < dataKeys.size(); index++) {
                statement.setString(index + 2, dataKeys.get(index));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.put(resultSet.getString("data_key"),
                            new SyncRecord(resultSet.getString("payload_json"), resultSet.getLong("updated_at")));
                }
            }
        } catch (SQLException exception) {
            logger.warn("从 MySQL 读取玩家 {} 的同步数据失败。", playerUuid, exception);
        }
        return records;
    }

    private static boolean saveBatch(UUID playerUuid, Map<String, String> payloads, long updatedAt) {
        HikariDataSource source = dataSource;
        if (source == null) {
            return false;
        }

        String sql = "INSERT INTO " + tableName + " (player_uuid, data_key, payload_json, updated_at) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE payload_json = IF(VALUES(updated_at) >= updated_at, VALUES(payload_json), payload_json), "
                + "updated_at = GREATEST(updated_at, VALUES(updated_at))";

        try (Connection connection = source.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            for (var entry : payloads.entrySet()) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, entry.getKey());
                statement.setString(3, entry.getValue());
                statement.setLong(4, updatedAt);
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
            return true;
        } catch (SQLException exception) {
            logger.warn("写入玩家 {} 的 MySQL 同步数据失败。", playerUuid, exception);
            return false;
        }
    }

    private static String buildJdbcUrl() {
        return "jdbc:mysql://" + SREConfig.instance().mysqlSyncHost + ":" + SREConfig.instance().mysqlSyncPort
                + "/" + SREConfig.instance().mysqlSyncDatabase
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL="
                + SREConfig.instance().mysqlSyncUseSsl;
    }

    private static void ensureSchema(Connection connection) throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "player_uuid CHAR(36) NOT NULL,"
                + "data_key VARCHAR(64) NOT NULL,"
                + "payload_json LONGTEXT NOT NULL,"
                + "updated_at BIGINT NOT NULL,"
                + "PRIMARY KEY (player_uuid, data_key),"
                + "KEY idx_updated_at (updated_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private static synchronized void shutdownDataSource() {
        HikariDataSource source = dataSource;
        dataSource = null;
        if (source != null) {
            source.close();
        }
    }

    private static List<String> normalizeKeys(Collection<String> dataKeys) {
        if (dataKeys == null || dataKeys.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String dataKey : dataKeys) {
            if (dataKey != null && !dataKey.isBlank() && !normalized.contains(dataKey)) {
                normalized.add(dataKey);
            }
        }
        return normalized;
    }

    private static Map<String, String> normalizePayloads(Map<String, String> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (var entry : payloads.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                normalized.put(entry.getKey(), entry.getValue());
            }
        }
        return normalized;
    }

    private static String sanitizeTablePrefix(String rawPrefix) {
        String prefix = rawPrefix == null ? "" : rawPrefix.trim();
        if (!TABLE_PREFIX_PATTERN.matcher(prefix).matches()) {
            logger.warn("MySQL 表前缀 {} 非法，回退为 sre_。", rawPrefix);
            return "sre_";
        }
        return prefix.isEmpty() ? "sre_" : prefix;
    }
}
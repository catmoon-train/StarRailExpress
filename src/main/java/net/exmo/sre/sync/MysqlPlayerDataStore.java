package net.exmo.sre.sync;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.wifi.starrailexpress.SREConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public final class MysqlPlayerDataStore {
    private static final Logger logger = LoggerFactory.getLogger(MysqlPlayerDataStore.class);
    private static final Pattern TABLE_PREFIX_PATTERN = Pattern.compile("[A-Za-z0-9_]*");
    private static final long FAST_FAIL_BACKOFF_MS = 15_000L;
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
    private static volatile long fastFailUntil = 0L;
    private static volatile boolean shutdownFlushMode = false;

    private MysqlPlayerDataStore() {
    }

    public record SyncRecord(String payload, long updatedAt) {
    }

    public static synchronized void initializeFromConfig() {
        shutdownDataSource();
        shutdownFlushMode = false;
        fastFailUntil = 0L;
        if (!SREConfig.instance().mysqlPlayerSyncEnabled) {
            return;
        }
        logger.info("MySQL 玩家数据同步已启用，数据库初始化中...");

        tableName = sanitizeTablePrefix(SREConfig.instance().mysqlSyncTablePrefix) + "player_sync_data";

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(buildJdbcUrl());
        hikariConfig.setUsername(SREConfig.instance().mysqlSyncUsername);
        hikariConfig.setPassword(SREConfig.instance().mysqlSyncPassword);
        hikariConfig.setPoolName("SRE-MySQLSync");
        hikariConfig.setMaximumPoolSize(Math.max(2, SREConfig.instance().mysqlSyncPoolSize));
        hikariConfig.setMinimumIdle(0);
        hikariConfig.setConnectionTimeout(getPoolConnectionTimeoutMs());
        hikariConfig.setValidationTimeout(getValidationTimeoutMs());
        // 保持服务器可启动，连接异常时交给后续的快速失败保护处理。
        hikariConfig.setInitializationFailTimeout(0L);
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
        } catch (Exception exception) {
            if (candidate != null) {
                candidate.close();
            }
            dataSource = null;
            logger.error("初始化 MySQL 玩家数据同步失败，已禁用数据库同步，服务器会继续启动。", exception);
        }
    }

    public static synchronized void shutdown() {
        shutdownDataSource();
    }

    public static void beginShutdownFlushMode() {
        shutdownFlushMode = true;
    }

    public static boolean isAvailable() {
        return dataSource != null;
    }

    public static CompletableFuture<Map<String, SyncRecord>> loadBatchAsync(UUID playerUuid,
            Collection<String> dataKeys) {
        List<String> normalizedKeys = normalizeKeys(dataKeys);
        if (playerUuid == null || normalizedKeys.isEmpty() || dataSource == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return CompletableFuture.supplyAsync(() -> loadBatch(playerUuid, normalizedKeys), EXECUTOR);
    }

    public static CompletableFuture<Boolean> saveBatchAsync(UUID playerUuid, Map<String, String> payloads,
            long updatedAt) {
        Map<String, String> normalizedPayloads = normalizePayloads(payloads);
        if (playerUuid == null || normalizedPayloads.isEmpty() || dataSource == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (shutdownFlushMode) {
            return CompletableFuture.completedFuture(saveBatch(playerUuid, normalizedPayloads, updatedAt));
        }
        return CompletableFuture.supplyAsync(() -> saveBatch(playerUuid, normalizedPayloads, updatedAt), EXECUTOR);
    }

    public static boolean saveBatchBlocking(UUID playerUuid, Map<String, String> payloads, long updatedAt,
            long timeoutMs) {
        if (isFastFailActive()) {
            return false;
        }
        try {
            return saveBatchAsync(playerUuid, payloads, updatedAt).get(Math.max(1000L, timeoutMs),
                    TimeUnit.MILLISECONDS);
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
        throwIfFastFailActive();

        String placeholders = String.join(",", Collections.nCopies(dataKeys.size(), "?"));
        String sql = "SELECT data_key, payload_json, updated_at FROM " + tableName
                + " WHERE player_uuid = ? AND data_key IN (" + placeholders + ")";
        Map<String, SyncRecord> records = new LinkedHashMap<>();
        try (Connection connection = source.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(getStatementTimeoutSeconds());
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
            clearFastFail();
        } catch (SQLException exception) {
            handleSqlFailure("读取", playerUuid, exception);
            throw new CompletionException(exception);
        }
        return records;
    }

    private static boolean saveBatch(UUID playerUuid, Map<String, String> payloads, long updatedAt) {
        HikariDataSource source = dataSource;
        if (source == null || isFastFailActive()) {
            return false;
        }

        String sql = "INSERT INTO " + tableName
                + " (player_uuid, data_key, payload_json, updated_at) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE payload_json = IF(VALUES(updated_at) >= updated_at, VALUES(payload_json), payload_json), "
                + "updated_at = GREATEST(updated_at, VALUES(updated_at))";

        Connection connection = null;
        try {
            connection = source.getConnection();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(getStatementTimeoutSeconds());
                for (var entry : payloads.entrySet()) {
                    statement.setString(1, playerUuid.toString());
                    statement.setString(2, entry.getKey());
                    statement.setString(3, entry.getValue());
                    statement.setLong(4, updatedAt);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
            clearFastFail();
            return true;
        } catch (SQLException exception) {
            rollbackQuietly(connection, playerUuid);
            handleSqlFailure("写入", playerUuid, exception);
            return false;
        } finally {
            closeQuietly(connection);
        }
    }

    private static void rollbackQuietly(Connection connection, UUID playerUuid) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            logger.warn("回滚玩家 {} 的 MySQL 同步事务失败。", playerUuid, rollbackException);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException closeException) {
            logger.debug("关闭 MySQL 连接失败。", closeException);
        }
    }

    private static boolean isFastFailActive() {
        return System.currentTimeMillis() < fastFailUntil;
    }

    private static void throwIfFastFailActive() {
        if (!isFastFailActive()) {
            return;
        }
        throw new CompletionException(new SQLException("MySQL sync is temporarily paused after recent connection failures."));
    }

    private static void clearFastFail() {
        fastFailUntil = 0L;
    }

    private static void handleSqlFailure(String operation, UUID playerUuid, SQLException exception) {
        if (shouldFastFail(exception)) {
            fastFailUntil = System.currentTimeMillis() + FAST_FAIL_BACKOFF_MS;
        }
        logger.warn("{}玩家 {} 的 MySQL 同步数据失败。", operation, playerUuid, exception);
    }

    private static boolean shouldFastFail(SQLException exception) {
        if (exception instanceof SQLTransientConnectionException
                || exception instanceof SQLRecoverableException
                || exception instanceof SQLNonTransientConnectionException) {
            return true;
        }
        String sqlState = exception.getSQLState();
        if (sqlState != null && sqlState.startsWith("08")) {
            return true;
        }
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String lowerMessage = message.toLowerCase(Locale.ROOT);
        return lowerMessage.contains("connection is not available")
                || lowerMessage.contains("communications link failure")
                || lowerMessage.contains("connection timed out")
                || lowerMessage.contains("connect timed out");
    }

    private static int getStatementTimeoutSeconds() {
        return Math.max(1, (int) Math.ceil(getEffectiveConfigTimeoutMs() / 1000.0));
    }

    private static int getSocketTimeoutMs() {
        return (int) Math.max(3000L, getEffectiveConfigTimeoutMs() * 2L);
    }

    private static int getConnectTimeoutMs() {
        return (int) getEffectiveConfigTimeoutMs();
    }

    private static int getValidationTimeoutMs() {
        return Math.min(getConnectTimeoutMs(), 2000);
    }

    private static int getPoolConnectionTimeoutMs() {
        return Math.max(2000, SREConfig.instance().mysqlSyncConnectTimeoutMs);
    }

    private static long getEffectiveConfigTimeoutMs() {
        return Math.max(1000L, SREConfig.instance().mysqlSyncConnectTimeoutMs);
    }

    private static String buildJdbcUrl() {
        return "jdbc:mysql://" + SREConfig.instance().mysqlSyncHost + ":" + SREConfig.instance().mysqlSyncPort
                + "/" + SREConfig.instance().mysqlSyncDatabase
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL="
                + SREConfig.instance().mysqlSyncUseSsl + "&connectTimeout=" + getConnectTimeoutMs()
                + "&socketTimeout=" + getSocketTimeoutMs();
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
            statement.setQueryTimeout(5);  // 5秒，超过抛出 SQLException
            statement.execute(ddl);
        }
    }

    private static synchronized void shutdownDataSource() {
        HikariDataSource source = dataSource;
        dataSource = null;
        shutdownFlushMode = false;
        fastFailUntil = 0L;
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

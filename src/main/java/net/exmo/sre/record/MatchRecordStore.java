package net.exmo.sre.record;

import com.zaxxer.hikari.HikariDataSource;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 全局战绩的远端数据库存储。
 *
 * <p>复用 {@link MysqlPlayerDataStore} 已建立的 HikariCP 连接池（即同一套 MySQL 配置与连接），
 * 仅额外维护一张 {@code <prefix>match_records} 表。表结构在首次访问时惰性建立。</p>
 */
public final class MatchRecordStore {

    private static final Logger logger = LoggerFactory.getLogger(MatchRecordStore.class);
    private static final int STATEMENT_TIMEOUT_SECONDS = 8;
    private static final int DEFAULT_LIST_LIMIT = 50;
    private static final int MAX_LIST_LIMIT = 200;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private int index = 1;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "sre-match-record-" + index++);
            thread.setDaemon(true);
            return thread;
        }
    });

    private static volatile boolean schemaReady = false;

    private MatchRecordStore() {
    }

    public static boolean isAvailable() {
        return MysqlPlayerDataStore.getDataSource() != null;
    }

    private static String tableName() {
        return MysqlPlayerDataStore.tablePrefix() + "match_records";
    }

    public static CompletableFuture<Boolean> saveAsync(MatchRecord record) {
        if (record == null || record.matchId == null || !isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> save(record), EXECUTOR);
    }

    public static CompletableFuture<List<MatchRecord.Summary>> listRecentAsync(int limit) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        int clamped = limit <= 0 ? DEFAULT_LIST_LIMIT : Math.min(limit, MAX_LIST_LIMIT);
        return CompletableFuture.supplyAsync(() -> listRecent(clamped), EXECUTOR);
    }

    public static CompletableFuture<Optional<MatchRecord>> loadAsync(String matchId) {
        if (matchId == null || matchId.isBlank() || !isAvailable()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(() -> load(matchId), EXECUTOR);
    }

    private static boolean save(MatchRecord record) {
        HikariDataSource source = MysqlPlayerDataStore.getDataSource();
        if (source == null) {
            return false;
        }
        String sql = "INSERT INTO " + tableName()
                + " (match_id, created_at, winning_team, player_count, summary_json, payload_json) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE created_at = VALUES(created_at), winning_team = VALUES(winning_team), "
                + "player_count = VALUES(player_count), summary_json = VALUES(summary_json), "
                + "payload_json = VALUES(payload_json)";
        try (Connection connection = source.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
                statement.setString(1, record.matchId);
                statement.setLong(2, record.createdAt);
                statement.setString(3, record.winningTeam);
                statement.setInt(4, record.playerCount);
                statement.setString(5, record.toSummaryJson());
                statement.setString(6, record.toJson());
                statement.executeUpdate();
            }
            return true;
        } catch (SQLException exception) {
            logger.warn("保存全局战绩 {} 到 MySQL 失败。", record.matchId, exception);
            return false;
        }
    }

    private static List<MatchRecord.Summary> listRecent(int limit) {
        HikariDataSource source = MysqlPlayerDataStore.getDataSource();
        List<MatchRecord.Summary> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        String sql = "SELECT summary_json FROM " + tableName() + " ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = source.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
                statement.setInt(1, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        MatchRecord.Summary summary = MatchRecord.summaryFromJson(resultSet.getString("summary_json"));
                        if (summary != null) {
                            result.add(summary);
                        }
                    }
                }
            }
        } catch (SQLException exception) {
            logger.warn("读取全局战绩列表失败。", exception);
        }
        return result;
    }

    private static Optional<MatchRecord> load(String matchId) {
        HikariDataSource source = MysqlPlayerDataStore.getDataSource();
        if (source == null) {
            return Optional.empty();
        }
        String sql = "SELECT payload_json FROM " + tableName() + " WHERE match_id = ? LIMIT 1";
        try (Connection connection = source.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
                statement.setString(1, matchId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.ofNullable(MatchRecord.fromJson(resultSet.getString("payload_json")));
                    }
                }
            }
        } catch (SQLException exception) {
            logger.warn("读取全局战绩 {} 失败。", matchId, exception);
        }
        return Optional.empty();
    }

    private static synchronized void ensureSchema(Connection connection) throws SQLException {
        if (schemaReady) {
            return;
        }
        String ddl = "CREATE TABLE IF NOT EXISTS " + tableName() + " ("
                + "match_id CHAR(36) NOT NULL,"
                + "created_at BIGINT NOT NULL,"
                + "winning_team VARCHAR(64) NULL,"
                + "player_count INT NOT NULL DEFAULT 0,"
                + "summary_json LONGTEXT NOT NULL,"
                + "payload_json LONGTEXT NOT NULL,"
                + "PRIMARY KEY (match_id),"
                + "KEY idx_created_at (created_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
            statement.execute(ddl);
        }
        schemaReady = true;
        logger.info("全局战绩表 {} 已就绪。", tableName());
    }
}

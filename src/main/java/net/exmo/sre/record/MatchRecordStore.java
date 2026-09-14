/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.exmo.sre.record;

import com.zaxxer.hikari.HikariDataSource;
import io.wifi.starrailexpress.network.TrafficRecorder;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

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
    private static final int MAX_SAVE_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MILLIS = 1_000L;

    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2, new ThreadFactory() {
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

    /** 一页战绩摘要 + 数据库内总条数（用于客户端虚拟列表的滚动条与按需拉取）。 */
    public record MatchPage(int total, int offset, List<MatchRecord.Summary> items) {
    }

    public static boolean isAvailable() {
        return MysqlPlayerDataStore.getDataSource() != null;
    }

    private static String tableName() {
        return MysqlPlayerDataStore.tablePrefix() + "match_records";
    }

    private static String playerTableName() {
        return MysqlPlayerDataStore.tablePrefix() + "match_record_players";
    }

    public static CompletableFuture<Boolean> saveAsync(MatchRecord record) {
        if (record == null || record.matchId == null || !isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        saveAttempt(record, 1, result);
        return result;
    }

    /**
     * 短暂的网络抖动不应让已结束的对局永久丢失。使用同一个 matchId 重试，数据库 UPSERT 保证幂等。
     * 长耗时 I/O 永远不阻塞服务端线程。
     */
    private static void saveAttempt(MatchRecord record, int attempt, CompletableFuture<Boolean> result) {
        CompletableFuture.supplyAsync(() -> save(record), EXECUTOR).whenComplete((saved, error) -> {
            if (error == null && Boolean.TRUE.equals(saved)) {
                result.complete(true);
                return;
            }
            if (attempt >= MAX_SAVE_ATTEMPTS) {
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(false);
                }
                return;
            }
            long delay = RETRY_DELAY_MILLIS * (1L << (attempt - 1));
            logger.warn("保存全局战绩 {} 第 {}/{} 次失败，{}ms 后重试。",
                    record.matchId, attempt, MAX_SAVE_ATTEMPTS, delay);
            EXECUTOR.schedule(() -> saveAttempt(record, attempt + 1, result), delay, TimeUnit.MILLISECONDS);
        });
    }

    /**
     * 按需拉取一页战绩（{@code offset} 起 {@code limit} 条，按时间倒序），并附带总条数。
     * 仅在客户端滚动到对应区间时才会调用——既减少网络流量，也只在需要时查询数据库。
     */
    public static CompletableFuture<MatchPage> listWindowAsync(int offset, int limit) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(new MatchPage(0, Math.max(0, offset), new ArrayList<>()));
        }
        int safeOffset = Math.max(0, offset);
        int clamped = limit <= 0 ? DEFAULT_LIST_LIMIT : Math.min(limit, MAX_LIST_LIMIT);
        return CompletableFuture.supplyAsync(() -> listWindow(safeOffset, clamped), EXECUTOR);
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
            connection.setAutoCommit(false);
            // SQL 流量统计：一个语句块记一次交互（批量与循环执行合并为一次），字节按行累计。
            TrafficRecorder.SqlStatement tracked = TrafficRecorder.sql("INSERT", tableName()).text(sql);
            try {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
                    String summaryJson = record.toSummaryJson();
                    String payloadJson = record.toJson();
                    statement.setString(1, record.matchId);
                    statement.setLong(2, record.createdAt);
                    statement.setString(3, record.winningTeam);
                    statement.setInt(4, record.playerCount);
                    statement.setString(5, summaryJson);
                    statement.setString(6, payloadJson);
                    statement.executeUpdate();
                    tracked.param(record.matchId)
                            .param(record.winningTeam)
                            .param(summaryJson)
                            .param(payloadJson)
                            .numbers(2);
                }
                replacePlayers(connection, record);
                connection.commit();
                tracked.record();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
            return true;
        } catch (SQLException exception) {
            logger.warn("保存全局战绩 {} 到 MySQL 失败。", record.matchId, exception);
            return false;
        }
    }

    /**
     * 为每名参赛者建立轻量索引行。网站按玩家查询时无需扫描/解析整张战绩 JSON 表，
     * 详情仍只在用户点击对应局时才读取完整 payload。
     */
    private static void replacePlayers(Connection connection, MatchRecord record) throws SQLException {
        String deleteSql = "DELETE FROM " + playerTableName() + " WHERE match_id = ?";
        TrafficRecorder.SqlStatement deleteTracked = TrafficRecorder.sql("DELETE", playerTableName())
                .text(deleteSql)
                .param(record.matchId);
        try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
            delete.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
            delete.setString(1, record.matchId);
            delete.executeUpdate();
        }
        deleteTracked.record();
        if (record.players == null || record.players.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + playerTableName()
                + " (match_id, player_uuid, created_at, player_json) VALUES (?, ?, ?, ?)";
        // 一次 executeBatch 记一次交互，字节把每行参数累加进去。
        TrafficRecorder.SqlStatement insertTracked = TrafficRecorder.sql("INSERT", playerTableName()).text(sql);
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
            for (MatchRecord.MatchPlayer player : record.players) {
                if (player == null || player.uuid == null || player.uuid.isBlank()) {
                    continue;
                }
                String playerJson = MatchRecord.GSON.toJson(player);
                insert.setString(1, record.matchId);
                insert.setString(2, player.uuid);
                insert.setLong(3, record.createdAt);
                insert.setString(4, playerJson);
                insert.addBatch();
                insertTracked.param(record.matchId).param(player.uuid).param(playerJson).numbers(1);
            }
            insert.executeBatch();
        }
        insertTracked.record();
    }

    private static MatchPage listWindow(int offset, int limit) {
        HikariDataSource source = MysqlPlayerDataStore.getDataSource();
        List<MatchRecord.Summary> items = new ArrayList<>();
        int total = 0;
        if (source == null) {
            return new MatchPage(0, offset, items);
        }
        String countSql = "SELECT COUNT(*) FROM " + tableName();
        String pageSql = "SELECT summary_json FROM " + tableName() + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        TrafficRecorder.SqlStatement countTracked = TrafficRecorder.sql("SELECT", tableName()).text(countSql);
        TrafficRecorder.SqlStatement pageTracked = TrafficRecorder.sql("SELECT", tableName())
                .text(pageSql)
                .numbers(2);
        try (Connection connection = source.getConnection()) {
            ensureSchema(connection);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
                try (ResultSet resultSet = statement.executeQuery(countSql)) {
                    if (resultSet.next()) {
                        total = resultSet.getInt(1);
                        countTracked.numbers(1);
                    }
                }
                countTracked.record();
            }
            try (PreparedStatement statement = connection.prepareStatement(pageSql)) {
                statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
                statement.setInt(1, limit);
                statement.setInt(2, offset);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String summaryJson = resultSet.getString("summary_json");
                        MatchRecord.Summary summary = MatchRecord.summaryFromJson(summaryJson);
                        if (summary != null) {
                            items.add(summary);
                        }
                        pageTracked.result(summaryJson);
                    }
                    pageTracked.record();
                }
            }
        } catch (SQLException exception) {
            logger.warn("读取全局战绩列表失败。", exception);
        }
        return new MatchPage(total, offset, items);
    }

    private static Optional<MatchRecord> load(String matchId) {
        HikariDataSource source = MysqlPlayerDataStore.getDataSource();
        if (source == null) {
            return Optional.empty();
        }
        String sql = "SELECT payload_json FROM " + tableName() + " WHERE match_id = ? LIMIT 1";
        TrafficRecorder.SqlStatement tracked = TrafficRecorder.sql("SELECT", tableName())
                .text(sql)
                .param(matchId);
        Optional<MatchRecord> loaded = Optional.empty();
        try (Connection connection = source.getConnection()) {
            ensureSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
                statement.setString(1, matchId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String payloadJson = resultSet.getString("payload_json");
                        loaded = Optional.ofNullable(MatchRecord.fromJson(payloadJson));
                        tracked.result(payloadJson);
                    }
                    tracked.record();
                }
            }
        } catch (SQLException exception) {
            logger.warn("读取全局战绩 {} 失败。", matchId, exception);
        }
        return loaded;
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
        String playerDdl = "CREATE TABLE IF NOT EXISTS " + playerTableName() + " ("
                + "match_id CHAR(36) NOT NULL,"
                + "player_uuid CHAR(36) NOT NULL,"
                + "created_at BIGINT NOT NULL,"
                + "player_json TEXT NOT NULL,"
                + "PRIMARY KEY (match_id, player_uuid),"
                + "KEY idx_player_created_at (player_uuid, created_at),"
                + "KEY idx_created_at (created_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        TrafficRecorder.SqlStatement tracked = TrafficRecorder.sql("DDL", tableName()).text(ddl);
        TrafficRecorder.SqlStatement playerTracked = TrafficRecorder.sql("DDL", playerTableName()).text(playerDdl);
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
            statement.execute(ddl);
            statement.execute(playerDdl);
        }
        tracked.record();
        playerTracked.record();
        schemaReady = true;
        logger.info("全局战绩表 {} 已就绪。", tableName());
    }
}

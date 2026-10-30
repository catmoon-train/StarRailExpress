package org.agmas.noellesroles.game.halloween.online;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 复用共享游戏库连接；一次批量事务写收件表，提交并核对内容后才确认本地收益。 */
public final class JdbcEarningsSink implements EarningsSink {
    @FunctionalInterface
    public interface Connections { Connection open() throws SQLException; }

    private final Connections connections;
    private final String table;
    private final int timeout;
    private final boolean createTable;
    private boolean schemaReady;

    public JdbcEarningsSink(Connections connections, String tableName, int timeoutSeconds, boolean createTable) {
        this.connections = Objects.requireNonNull(connections);
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]{1,64}") || timeoutSeconds < 1 || timeoutSeconds > 60)
            throw new IllegalArgumentException("Invalid halloween database settings");
        this.table = "`" + tableName + "`";
        this.timeout = timeoutSeconds;
        this.createTable = createTable;
    }

    /** 表的 ID 全局唯一；processed_at 只由官网入账后更新，模组重放不能清空它。 */
    public static String createTableSql(String tableName) {
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]{1,64}"))
            throw new IllegalArgumentException("Invalid halloween earnings table name");
        return "CREATE TABLE IF NOT EXISTS `" + tableName + "` ("
                + "id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,"
                + "server_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,"
                + "minecraft_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,"
                + "amount INT NOT NULL,earned_at BIGINT NOT NULL,"
                + "received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),"
                + "processed_at DATETIME(3) NULL,PRIMARY KEY(id),"
                + "KEY pending(processed_at,received_at,id)) ENGINE=InnoDB";
    }

    private void ensureSchema(Connection connection) throws SQLException {
        if (schemaReady) return;
        String name = table.substring(1, table.length() - 1);
        if (createTable) {
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(timeout);
                statement.executeUpdate(createTableSql(name));
            }
        }
        // 旧表若不是事务引擎，rollback 不可靠；明确失败，不能把写入误认为可靠提交。
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            statement.setQueryTimeout(timeout);
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !"InnoDB".equalsIgnoreCase(rows.getString(1)))
                    throw new SQLException("Halloween earnings table must exist and use InnoDB");
            }
        }
        schemaReady = true;
    }

    @Override
    public synchronized Set<UUID> persist(UUID serverId, List<Earning> entries) throws IOException {
        Objects.requireNonNull(serverId);
        if (entries == null || entries.isEmpty() || entries.size() > 128) throw new IllegalArgumentException("Invalid batch size");
        Map<UUID, Earning> expected = new LinkedHashMap<>();
        for (Earning entry : entries) {
            if (expected.putIfAbsent(entry.id(), entry) != null) throw new IllegalArgumentException("Duplicate batch earning id");
        }
        List<Earning> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(entry -> entry.id().toString()));
        try (Connection connection = connections.open()) {
            ensureSchema(connection); // DDL 在收益事务前完成，避免隐式提交。
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table
                        + " (id,server_id,minecraft_uuid,amount,earned_at) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id")) {
                    statement.setQueryTimeout(timeout);
                    for (Earning entry : sorted) {
                        statement.setString(1, entry.id().toString());
                        statement.setString(2, serverId.toString());
                        statement.setString(3, entry.minecraftUuid().toString());
                        statement.setInt(4, entry.amount());
                        statement.setLong(5, entry.earnedAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                // 不按 INSERT 影响行数确认：重复 ID 必须逐字段相同，碰撞则整批回滚。
                String placeholders = String.join(",", java.util.Collections.nCopies(sorted.size(), "?"));
                Set<UUID> confirmed = new HashSet<>();
                try (PreparedStatement statement = connection.prepareStatement("SELECT id,server_id,minecraft_uuid,amount,earned_at FROM "
                        + table + " WHERE id IN (" + placeholders + ") FOR UPDATE")) {
                    statement.setQueryTimeout(timeout);
                    for (int i = 0; i < sorted.size(); i++) statement.setString(i + 1, sorted.get(i).id().toString());
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            UUID id = Earning.canonicalUuid(rows.getString("id"));
                            Earning entry = expected.get(id);
                            if (entry == null || !serverId.toString().equals(rows.getString("server_id"))
                                    || !entry.minecraftUuid().toString().equals(rows.getString("minecraft_uuid"))
                                    || entry.amount() != rows.getInt("amount") || entry.earnedAt() != rows.getLong("earned_at")
                                    || !confirmed.add(id)) throw new SQLException("Conflicting halloween earning record");
                        }
                    }
                }
                if (confirmed.size() != expected.size()) throw new SQLException("Incomplete halloween earning batch");
                connection.commit();
                return Set.copyOf(confirmed);
            } catch (SQLException | RuntimeException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch (SQLException | RuntimeException ex) {
            schemaReady = false;
            // JDBC 异常可能含连接地址、用户名等，交给外层按类别节流，不传播原文。
            throw new IOException("Halloween database batch not acknowledged");
        }
    }
}

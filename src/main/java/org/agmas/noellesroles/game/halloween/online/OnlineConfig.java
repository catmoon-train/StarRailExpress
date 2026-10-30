package org.agmas.noellesroles.game.halloween.online;


/** 南瓜收益数据库队列配置；连接凭据复用服务器已有 MySQL 配置，绝不发给客户端。 */
public final class OnlineConfig {
    public boolean enabled = false;
    /** 留空时使用既有 MySQL 表前缀 + halloween_earnings。 */
    public String earningsTable = "";
    public boolean createTableIfMissing = true;
    public int flushIntervalSeconds = 30;
    public int batchSize = 128;
    public int statementTimeoutSeconds = 10;

    /** 将服务器主配置复制成独立运行快照；不读写独立配置文件。 */
    public static OnlineConfig fromSettings(boolean enabled, String earningsTable, boolean createTableIfMissing,
                                            int flushIntervalSeconds, int batchSize, int statementTimeoutSeconds) {
        OnlineConfig config = new OnlineConfig();
        config.enabled = enabled;
        config.earningsTable = earningsTable;
        config.createTableIfMissing = createTableIfMissing;
        config.flushIntervalSeconds = flushIntervalSeconds;
        config.batchSize = batchSize;
        config.statementTimeoutSeconds = statementTimeoutSeconds;
        config.validate();
        return config;
    }

    public void validate() {
        if (batchSize < 1 || batchSize > 128 || flushIntervalSeconds < 1 || flushIntervalSeconds > 86400
                || statementTimeoutSeconds < 1 || statementTimeoutSeconds > 60)
            throw new IllegalArgumentException("Invalid halloween database timing/batch limits");
        if (earningsTable == null || !earningsTable.isEmpty() && !earningsTable.matches("[A-Za-z0-9_]{1,64}"))
            throw new IllegalArgumentException("Invalid halloween earnings table name");
    }

    public String tableName(String prefix) {
        validate();
        String table = earningsTable.isEmpty() ? prefix + "halloween_earnings" : earningsTable;
        if (!table.matches("[A-Za-z0-9_]{1,64}")) throw new IllegalArgumentException("Invalid halloween earnings table name");
        return table;
    }
}

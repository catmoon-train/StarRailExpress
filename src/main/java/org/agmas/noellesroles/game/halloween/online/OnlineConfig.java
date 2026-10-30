package org.agmas.noellesroles.game.halloween.online;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 南瓜收益数据库队列配置；连接凭据复用服务器已有 MySQL 配置，绝不发给客户端。 */
public final class OnlineConfig {
    public boolean enabled = false;
    /** 留空时使用既有 MySQL 表前缀 + halloween_earnings。 */
    public String earningsTable = "";
    public boolean createTableIfMissing = true;
    public int flushIntervalSeconds = 30;
    public int batchSize = 128;
    public int statementTimeoutSeconds = 10;

    public static OnlineConfig load(Path file) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(new OnlineConfig()) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            OnlineConfig config = gson.fromJson(reader, OnlineConfig.class);
            if (config == null) throw new IOException("Empty halloween-online configuration");
            return config;
        } catch (RuntimeException ex) {
            // 旧文件可能仍含已经弃用的 HTTP 密钥，错误时也不输出配置原文。
            throw new IOException("Invalid halloween-online configuration JSON");
        }
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

package org.agmas.noellesroles.game.halloween.online;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/** 仅在主配置首次加载前迁移六项；不接触连接开关、凭据或世界 WAL。 */
public final class OnlineConfigMigration {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final String[] OLD_KEYS = {"enabled", "earningsTable", "createTableIfMissing",
            "flushIntervalSeconds", "batchSize", "statementTimeoutSeconds"};
    private static final String[] NEW_KEYS = {"mysqlHalloweenSyncEnabled", "mysqlHalloweenEarningsTable",
            "mysqlHalloweenCreateTableIfMissing", "mysqlHalloweenFlushIntervalSeconds",
            "mysqlHalloweenBatchSize", "mysqlHalloweenStatementTimeoutSeconds"};

    private OnlineConfigMigration() {}

    /** 任意新字段（包括 false/null）都表示主配置已接管，不再读取旧文件。 */
    public static boolean migrate(Path main, Path legacy) throws IOException {
        return migrate(main, legacy, (source, target) -> Files.move(source, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    @FunctionalInterface
    interface Replacer {
        void replace(Path source, Path target) throws IOException;
    }

    static boolean migrate(Path main, Path legacy, Replacer replacer) throws IOException {
        try {
            byte[] original = Files.exists(main) ? Files.readAllBytes(main) : null;
            JsonObject primary = original == null ? new JsonObject() : readObject(main);
            for (String key : NEW_KEYS) if (primary.has(key)) return false;
            if (Files.notExists(legacy)) return false; // 不创建旧文件，也不为缺旧文件写主配置。
            JsonObject old = readObject(legacy);
            OnlineConfig defaults = new OnlineConfig();
            OnlineConfig settings = OnlineConfig.fromSettings(
                    bool(old, OLD_KEYS[0], defaults.enabled),
                    text(old, OLD_KEYS[1], defaults.earningsTable),
                    bool(old, OLD_KEYS[2], defaults.createTableIfMissing),
                    integer(old, OLD_KEYS[3], defaults.flushIntervalSeconds),
                    integer(old, OLD_KEYS[4], defaults.batchSize),
                    integer(old, OLD_KEYS[5], defaults.statementTimeoutSeconds));
            JsonObject converted = JSON.toJsonTree(settings).getAsJsonObject();
            for (int i = 0; i < NEW_KEYS.length; i++) primary.add(NEW_KEYS[i], converted.get(OLD_KEYS[i]));
            Path temporary = Files.createTempFile(main.toAbsolutePath().getParent(), ".sre-halloween-", ".tmp");
            try {
                byte[] bytes = (JSON.toJson(primary) + "\n").getBytes(StandardCharsets.UTF_8);
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                // 不覆盖启动期间被其他写入者修改的配置。
                if (original == null ? Files.exists(main)
                        : !Arrays.equals(original, Files.readAllBytes(main))) throw new IOException();
                // 不支持原子替换时失败关闭，不降级成先截断原文件再写。
                replacer.replace(temporary, main);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return true; // 只有替换成功后，调用方才能从主配置加载到内存。
        } catch (IOException | RuntimeException ex) {
            // 旧 JSON 可能包含 HTTP 密钥，主配置包含数据库密码；不附加原异常。
            throw new IOException("Halloween configuration migration failed; original files retained");
        }
    }

    private static JsonObject readObject(Path file) throws IOException {
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
            reader.setLenient(false);
            JsonElement value = JSON.getAdapter(JsonElement.class).read(reader);
            if (value == null || !value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException();
            return value.getAsJsonObject();
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
        return value.getAsBoolean();
    }

    private static String text(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        return value.getAsString();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        return value.getAsBigDecimal().intValueExact();
    }
}

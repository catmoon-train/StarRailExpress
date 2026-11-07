package net.exmo.sre.sync;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MysqlPlayerDataStoreRevisionTest {
    @TempDir java.nio.file.Path tempConfig;

    @Test void tenWebsiteGrantsAfterCommitCannotBlessStaleGamePayload() throws Exception {
        exercise(false);
    }
    @Test void failedCommitNeverPublishesUncommittedRevision() throws Exception {
        exercise(true);
    }
    private void exercise(boolean failCommit) throws Exception {
        // 普通 JUnit 进程没有游戏 provider，使用测试框架管理的临时配置目录。
        Object loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        Field configDir = loader.getClass().getDeclaredField("configDir");
        configDir.setAccessible(true);
        Object originalConfigDir = configDir.get(loader);
        try {
            configDir.set(loader, tempConfig);
            exerciseRevision(failCommit);
        } finally {
            configDir.set(loader, originalConfigDir);
        }
    }

    @SuppressWarnings("unchecked")
    private void exerciseRevision(boolean failCommit) throws Exception {
        Field revisionsField = MysqlPlayerDataStore.class.getDeclaredField("KNOWN_REVISIONS");
        revisionsField.setAccessible(true);
        Map<Object, Long> revisions = (Map<Object, Long>) revisionsField.get(null);
        Map<Object, Long> original = new HashMap<>(revisions);
        Field sourceField = MysqlPlayerDataStore.class.getDeclaredField("dataSource");
        sourceField.setAccessible(true);
        Object oldSource = sourceField.get(null);
        UUID uuid = UUID.randomUUID();
        Class<?> keyType = Class.forName(MysqlPlayerDataStore.class.getName() + "$RecordKey");
        Constructor<?> keyConstructor = keyType.getDeclaredConstructor(UUID.class, String.class);
        keyConstructor.setAccessible(true);
        Object key = keyConstructor.newInstance(uuid, "backpack");
        revisions.put(key, 1L);
        long[] databaseRevision = {1};
        List<String> events = new ArrayList<>();
        Connection connection = proxy(Connection.class, (object, method, args) -> {
            switch (method.getName()) {
                case "prepareStatement": {
                    String sql = (String) args[0];
                    Map<Integer, Object> bindings = new HashMap<>();
                    return proxy(PreparedStatement.class, (o, m, a) -> {
                        if (m.getName().startsWith("set") && a != null && a.length == 2) { bindings.put((Integer) a[0], a[1]); return null; }
                        if (m.getName().equals("executeUpdate")) {
                            events.add("WRITE");
                            if ((long) bindings.get(7) != databaseRevision[0]) return 0;
                            databaseRevision[0]++; return 1;
                        }
                        if (m.getName().equals("executeQuery")) {
                            events.add("READ");
                            long observed = databaseRevision[0];
                            boolean[] next = {true};
                            return proxy(ResultSet.class, (r, rm, ra) -> switch (rm.getName()) {
                                case "next" -> { boolean value = next[0]; next[0] = false; yield value; }
                                case "getString" -> "backpack";
                                case "getLong" -> observed;
                                default -> defaultValue(rm.getReturnType());
                            });
                        }
                        return defaultValue(m.getReturnType());
                    });
                }
                case "commit":
                    events.add("COMMIT");
                    if (failCommit) throw new SQLException("injected commit failure");
                    // Website commits ten independent grants immediately after game releases locks.
                    databaseRevision[0] += 10; return null;
                case "rollback": events.add("ROLLBACK"); return null;
                default: return defaultValue(method.getReturnType());
            }
        });
        HikariDataSource fake = new HikariDataSource() {
            @Override public Connection getConnection() { return connection; }
        };
        Method save = MysqlPlayerDataStore.class.getDeclaredMethod("saveBatch", UUID.class, Map.class, long.class, boolean.class);
        save.setAccessible(true);
        try {
            sourceField.set(null, fake);
            assertEquals(!failCommit, save.invoke(null, uuid, Map.of("backpack", "{\"cards\":{\"KILLER\":0}}"), 1L, false));
            assertTrue(events.indexOf("READ") < events.indexOf("COMMIT"), events.toString());
            assertEquals(failCommit ? 1L : 2L, revisions.get(key));
            if (!failCommit) {
                assertEquals(12L, databaseRevision[0]);
                assertEquals(false, save.invoke(null, uuid, Map.of("backpack", "stale"), 2L, false));
                assertEquals(12L, databaseRevision[0]);
            }
        } finally {
            sourceField.set(null, oldSource);
            revisions.clear(); revisions.putAll(original);
            fake.close();
        }
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}

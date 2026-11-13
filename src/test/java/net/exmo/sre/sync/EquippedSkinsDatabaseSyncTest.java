package net.exmo.sre.sync;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class EquippedSkinsDatabaseSyncTest {
    @TempDir Path tempConfig;
    private Object loader;
    private Field configDir;
    private Object oldConfigDir;
    private Object oldSource;
    private HikariDataSource fake;
    private final List<String> uploads = new ArrayList<>();
    private CountDownLatch entered;
    private CountDownLatch release;
    private boolean fail;

    @BeforeEach
    void setUp() throws Exception {
        loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        configDir = loader.getClass().getDeclaredField("configDir");
        configDir.setAccessible(true);
        oldConfigDir = configDir.get(loader);
        configDir.set(loader, tempConfig);
        oldSource = field("dataSource").get(null);
        fake = new HikariDataSource() {
            @Override public Connection getConnection() {
                return proxy(Connection.class, (object, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        String sql = (String) args[0];
                        assertTrue(sql.contains("VALUES (?, 'skins', JSON_OBJECT('equipped', JSON_EXTRACT(?, '$'))"));
                        assertTrue(sql.contains("payload_json = JSON_SET(payload_json, '$.equipped', JSON_EXTRACT(?, '$'))"));
                        assertTrue(sql.contains("record_version = record_version + 1"));
                        assertFalse(sql.contains("economy"));
                        assertFalse(sql.contains("unlocked"));
                        assertFalse(sql.contains("coinNum"));
                        Map<Integer, Object> bindings = new HashMap<>();
                        return proxy(PreparedStatement.class, (statement, operation, values) -> {
                            if (operation.getName().startsWith("set") && values != null && values.length == 2) {
                                bindings.put((Integer) values[0], values[1]);
                            }
                            if (operation.getName().equals("executeUpdate")) {
                                assertEquals(bindings.get(2), bindings.get(6));
                                assertEquals("game_server", bindings.get(5));
                                if (entered != null && uploads.isEmpty()) {
                                    entered.countDown();
                                    assertTrue(release.await(5, TimeUnit.SECONDS));
                                }
                                if (fail) throw new SQLException("injected statement failure", "42000");
                                uploads.add((String) bindings.get(2));
                                return 1;
                            }
                            return defaultValue(operation.getReturnType());
                        });
                    }
                    return defaultValue(method.getReturnType());
                });
            }
        };
        field("dataSource").set(null, fake);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (release != null) release.countDown();
        MysqlPlayerDataStore.awaitEquippedSkinsUploads(5000);
        field("dataSource").set(null, oldSource);
        configDir.set(loader, oldConfigDir);
        fake.close();
    }

    @Test
    void updatesOnlyEquippedInAuthoritativeSkinsPartition() throws Exception {
        String equipped = "{\"knife\":\"gold\",\"hat\":\"default\"}";
        assertTrue(MysqlPlayerDataStore.saveEquippedSkinsAsync(UUID.randomUUID(), equipped, 100L)
                .get(5, TimeUnit.SECONDS));
        assertEquals(List.of(equipped), uploads);
    }

    @Test
    void emptyEquipmentReplacesPreviousEquipment() {
        assertTrue(MysqlPlayerDataStore.saveEquippedSkinsBlocking(UUID.randomUUID(), "{}", 100L, 5000));
        assertEquals(List.of("{}"), uploads);
    }

    @Test
    void uploadsForSamePlayerRemainInSubmissionOrder() throws Exception {
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        UUID uuid = UUID.randomUUID();
        CompletableFuture<Boolean> first = MysqlPlayerDataStore.saveEquippedSkinsAsync(uuid, "{\"knife\":\"gold\"}", 100L);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        CompletableFuture<Boolean> second = MysqlPlayerDataStore.saveEquippedSkinsAsync(uuid, "{\"knife\":\"default\"}", 101L);
        assertFalse(second.isDone());
        release.countDown();
        assertTrue(first.get(5, TimeUnit.SECONDS));
        assertTrue(second.get(5, TimeUnit.SECONDS));
        assertEquals(List.of("{\"knife\":\"gold\"}", "{\"knife\":\"default\"}"), uploads);
    }

    @Test
    void failedWriteIsNotReportedAsSuccessAndCanBeRetried() {
        UUID uuid = UUID.randomUUID();
        fail = true;
        assertFalse(MysqlPlayerDataStore.saveEquippedSkinsBlocking(uuid, "{}", 100L, 5000));
        assertTrue(uploads.isEmpty());
        fail = false;
        assertTrue(MysqlPlayerDataStore.saveEquippedSkinsBlocking(uuid, "{}", 101L, 5000));
    }

    private static Field field(String name) throws Exception {
        Field field = MysqlPlayerDataStore.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
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

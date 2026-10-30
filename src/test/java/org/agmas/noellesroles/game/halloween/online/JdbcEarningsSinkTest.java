package org.agmas.noellesroles.game.halloween.online;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** 执行生产 JDBC 路径，模拟事务与断线；不是实服 MySQL 集成测试。 */
class JdbcEarningsSinkTest {
    @TempDir Path temp;
    static final UUID SERVER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    static final UUID PLAYER = UUID.fromString("44444444-4444-4444-8444-444444444444");
    Earning earning(int amount) { return new Earning(UUID.randomUUID(), PLAYER, amount, 1792368000000L); }
    JdbcEarningsSink sink(FakeDatabase db) { return new JdbcEarningsSink(db::open, "sre_halloween_earnings", 10, true); }

    @Test void writes128WithOneBatchAndVerifiesAllFieldsBeforeCommit() throws Exception {
        FakeDatabase db = new FakeDatabase(); List<Earning> batch = new ArrayList<>();
        for (int i = 0; i < 128; i++) batch.add(earning(i + 1));
        Set<UUID> ack = sink(db).persist(SERVER, batch);
        assertEquals(128, ack.size()); assertEquals(128, db.committed.size()); assertEquals(1, db.batchCalls);
        assertEquals(1, db.commits); assertEquals(0, db.rollbacks);
        assertEquals(List.of("ddl", "engine", "begin", "batch", "select", "commit", "close"), db.events);
        assertEquals(batch.stream().map(e -> e.id().toString()).sorted().toList(), db.insertOrder);
        assertTrue(db.timeouts.stream().allMatch(n -> n == 10));
        for (Earning e : batch) assertEquals(new Stored(SERVER, e, false), db.committed.get(e.id()));
    }

    @Test void duplicateAlreadyProcessedRowIsAcknowledgedWithoutChangingItsReceipt() throws Exception {
        FakeDatabase db = new FakeDatabase(); Earning e = earning(7);
        Stored processed = new Stored(SERVER, e, true); db.committed.put(e.id(), processed);
        JdbcEarningsSink sink = sink(db);
        assertEquals(Set.of(e.id()), sink.persist(SERVER, List.of(e)));
        assertEquals(processed, db.committed.get(e.id()));
        assertEquals(Set.of(e.id()), sink.persist(SERVER, List.of(e)));
        assertEquals(1, db.ddls); assertEquals(1, db.committed.size());
        assertTrue(db.sql.stream().filter(s -> s.startsWith("INSERT")).noneMatch(s -> s.contains("processed_at")));
    }

    @Test void idCollisionOnAnyPayloadFieldRollsBackEntireBatch() {
        Earning old = earning(7);
        List<Stored> conflicts = List.of(
                new Stored(UUID.randomUUID(), old, true),
                new Stored(SERVER, new Earning(old.id(), UUID.randomUUID(), 7, old.earnedAt()), true),
                new Stored(SERVER, new Earning(old.id(), PLAYER, 8, old.earnedAt()), true),
                new Stored(SERVER, new Earning(old.id(), PLAYER, 7, old.earnedAt() + 1), true));
        for (Stored conflict : conflicts) {
            FakeDatabase db = new FakeDatabase(); db.committed.put(old.id(), conflict);
            Earning fresh = earning(3);
            IOException error = assertThrows(IOException.class, () -> sink(db).persist(SERVER, List.of(fresh, old)));
            assertEquals(Map.of(old.id(), conflict), db.committed); assertEquals(0, db.commits); assertEquals(1, db.rollbacks);
            assertFalse(error.toString().contains("private-db-password"));
        }
    }

    @Test void partialBatchFailureAndIncompleteVerificationCannotAcknowledge() {
        for (boolean insertFailure : new boolean[]{true, false}) {
            FakeDatabase db = new FakeDatabase(); db.failBatch = insertFailure; db.incompleteSelect = !insertFailure;
            assertThrows(IOException.class, () -> sink(db).persist(SERVER, List.of(earning(1), earning(2))));
            assertTrue(db.committed.isEmpty()); assertEquals(0, db.commits); assertEquals(1, db.rollbacks);
        }
    }

    @Test void commitFailureRetainsWalEvenIfDatabaseActuallyCommittedAndReplayIsSafe() throws Exception {
        FakeDatabase db = new FakeDatabase(); db.failAfterCommit = true;
        Earning e = earning(7); AtomicLong time = new AtomicLong(e.earnedAt());
        try (EarningsStore store = new EarningsStore(temp)) {
            EarningsSender sender = new EarningsSender(store, sink(db), new OnlineConfig(), time::get, () -> 0, ignored -> {});
            sender.record(e); sender.requestFlush(); assertFalse(sender.tick());
            assertEquals(List.of(e), store.pending(128)); assertEquals(1, db.committed.size());
            time.addAndGet(1000); assertTrue(sender.tick());
            assertEquals(0, store.pendingCount()); assertEquals(1, db.committed.size()); assertEquals(2, db.commits);
        }
    }

    @Test void missingOrNonTransactionalTableFailsClosedAndManualInstallationCanRecover() throws Exception {
        FakeDatabase badEngine = new FakeDatabase(); badEngine.engine = "MyISAM";
        assertThrows(IOException.class, () -> sink(badEngine).persist(SERVER, List.of(earning(1))));
        assertEquals(0, badEngine.batchCalls);
        FakeDatabase missing = new FakeDatabase(); missing.exists = false;
        JdbcEarningsSink sink = new JdbcEarningsSink(missing::open, "sre_halloween_earnings", 10, false);
        Earning e = earning(7);
        assertThrows(IOException.class, () -> sink.persist(SERVER, List.of(e))); assertEquals(0, missing.ddls);
        missing.exists = true;
        assertEquals(Set.of(e.id()), sink.persist(SERVER, List.of(e)));
        assertEquals(0, missing.ddls);
    }

    @Test void databaseUnavailableDoesNotExposeConnectionCredentials() {
        JdbcEarningsSink sink = new JdbcEarningsSink(() -> { throw new SQLException("jdbc:mysql://private-db-password"); }, "sre_halloween_earnings", 10, false);
        IOException error = assertThrows(IOException.class, () -> sink.persist(SERVER, List.of(earning(1))));
        assertEquals("Halloween database batch not acknowledged", error.getMessage()); assertNull(error.getCause());
    }

    @Test void duplicateLocalIdsUnsafeIdentifiersAndOversizedBatchesAreRejected() {
        FakeDatabase db = new FakeDatabase(); Earning e = earning(1); JdbcEarningsSink sink = sink(db);
        assertThrows(IllegalArgumentException.class, () -> sink.persist(SERVER, List.of(e, e)));
        assertThrows(IllegalArgumentException.class, () -> sink.persist(SERVER, List.of()));
        assertThrows(IllegalArgumentException.class, () -> sink.persist(SERVER, java.util.Collections.nCopies(129, e)));
        for (String bad : new String[]{"", "db.table", "x` SET", "a".repeat(65)}) {
            assertThrows(IllegalArgumentException.class, () -> JdbcEarningsSink.createTableSql(bad));
            assertThrows(IllegalArgumentException.class, () -> new JdbcEarningsSink(db::open, bad, 10, true));
        }
        assertEquals(0, db.opens);
    }

    private record Stored(UUID server, Earning entry, boolean processed) {}

    /** 严格 JDBC 替身：执行真实参数绑定、批写、读取、commit/rollback 顺序，未知调用直接失败。 */
    private static final class FakeDatabase {
        Map<UUID, Stored> committed = new LinkedHashMap<>();
        final List<String> events = new ArrayList<>(), sql = new ArrayList<>(), insertOrder = new ArrayList<>();
        final List<Integer> timeouts = new ArrayList<>();
        int batchCalls, commits, rollbacks, ddls, opens;
        boolean exists = true, failBatch, incompleteSelect, failAfterCommit;
        String engine = "InnoDB";

        Connection open() {
            opens++;
            class Transaction {
                Map<UUID, Stored> working;
                Connection connection() {
                    return proxy(Connection.class, (p, method, args) -> switch (method.getName()) {
                        case "createStatement" -> ddl();
                        case "prepareStatement" -> prepared((String) args[0]);
                        case "setAutoCommit" -> { assertEquals(false, args[0]); events.add("begin"); working = new LinkedHashMap<>(committed); yield null; }
                        case "commit" -> {
                            events.add("commit"); commits++; committed = new LinkedHashMap<>(working); working = null;
                            if (failAfterCommit) { failAfterCommit = false; throw new SQLException("private-db-password: response lost"); }
                            yield null;
                        }
                        case "rollback" -> { events.add("rollback"); rollbacks++; working = null; yield null; }
                        case "close" -> { events.add("close"); yield null; }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
                }
                Statement ddl() {
                    return proxy(Statement.class, (p, method, args) -> switch (method.getName()) {
                        case "setQueryTimeout" -> { timeouts.add((Integer) args[0]); yield null; }
                        case "executeUpdate" -> { assertNull(working, "DDL must not implicitly commit earnings"); sql.add((String) args[0]); events.add("ddl"); ddls++; exists = true; yield 0; }
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
                }
                PreparedStatement prepared(String query) {
                    sql.add(query); Map<Integer, Object> params = new HashMap<>(); List<Map<Integer, Object>> batch = new ArrayList<>();
                    return proxy(PreparedStatement.class, (p, method, args) -> switch (method.getName()) {
                        case "setQueryTimeout" -> { timeouts.add((Integer) args[0]); yield null; }
                        case "setString", "setInt", "setLong" -> { params.put((Integer) args[0], args[1]); yield null; }
                        case "addBatch" -> { batch.add(new HashMap<>(params)); yield null; }
                        case "executeBatch" -> {
                            assertNotNull(working); assertTrue(query.contains("ON DUPLICATE KEY UPDATE id=id"));
                            events.add("batch"); batchCalls++;
                            for (Map<Integer, Object> values : batch) {
                                UUID id = UUID.fromString((String) values.get(1));
                                Earning e = new Earning(id, UUID.fromString((String) values.get(3)), (Integer) values.get(4), (Long) values.get(5));
                                insertOrder.add(id.toString());
                                working.putIfAbsent(id, new Stored(UUID.fromString((String) values.get(2)), e, false));
                                if (failBatch) throw new SQLException("partial insert failure");
                            }
                            yield new int[batch.size()];
                        }
                        case "executeQuery" -> {
                            if (query.contains("information_schema.TABLES")) {
                                events.add("engine"); yield rows(exists ? List.of(Map.of("1", engine)) : List.of());
                            }
                            assertNotNull(working); assertTrue(query.endsWith("FOR UPDATE")); events.add("select");
                            List<Map<String, Object>> selected = new ArrayList<>();
                            for (int i = 1; i <= params.size(); i++) {
                                Stored stored = working.get(UUID.fromString((String) params.get(i)));
                                if (stored == null || incompleteSelect && i == 1) continue;
                                Earning e = stored.entry();
                                selected.add(Map.of("id", e.id().toString(), "server_id", stored.server().toString(), "minecraft_uuid", e.minecraftUuid().toString(), "amount", e.amount(), "earned_at", e.earnedAt()));
                            }
                            yield rows(selected);
                        }
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
                }
            }
            return new Transaction().connection();
        }
    }

    private static ResultSet rows(List<? extends Map<String, ?>> rows) {
        int[] index = {-1};
        return proxy(ResultSet.class, (p, method, args) -> switch (method.getName()) {
            case "next" -> ++index[0] < rows.size();
            case "getString" -> rows.get(index[0]).get(args[0].toString()).toString();
            case "getInt" -> ((Number) rows.get(index[0]).get(args[0].toString())).intValue();
            case "getLong" -> ((Number) rows.get(index[0]).get(args[0].toString())).longValue();
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(JdbcEarningsSinkTest.class.getClassLoader(), new Class<?>[]{type}, handler));
    }
}

package io.wifi.srepackcompat.startup;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class NeighbourTableFactoryTest {
    private record Key(String name) {
        @Override public int hashCode() { return 1; }
    }

    private <R, C, V> void assertIdentical(Table<R, C, V> source, ArrayTable<R, C, V> actual) {
        var expected = ArrayTable.create(source);
        assertEquals(expected.rowKeyList(), actual.rowKeyList());
        assertEquals(expected.columnKeyList(), actual.columnKeyList());
        for (int i = 0; i < expected.rowKeyList().size(); i++) assertSame(expected.rowKeyList().get(i), actual.rowKeyList().get(i));
        for (int i = 0; i < expected.columnKeyList().size(); i++) assertSame(expected.columnKeyList().get(i), actual.columnKeyList().get(i));
        assertEquals(expected, actual);
        for (R row : expected.rowKeyList()) {
            for (C column : expected.columnKeyList()) assertSame(expected.get(row, column), actual.get(row, column));
        }
    }

    @Test
    void reusesOnlyImmutableKeyIndexesAndNeverSharesWritableCells() {
        var cache = new NeighbourTableFactory.LayoutCache(8);
        var source = HashBasedTable.<String, String, Object>create();
        source.put("a", "x", new Object());
        source.put("b", "y", new Object());
        var first = cache.create(source);
        var second = cache.create(source);
        assertIdentical(source, first);
        assertIdentical(source, second);
        assertSame(first.rowKeyList(), second.rowKeyList());
        assertSame(first.columnKeyList(), second.columnKeyList());
        first.put("a", "x", new Object());
        first.put("a", "y", new Object());
        assertIdentical(source, second);
        assertIdentical(source, cache.create(source));
        assertEquals(1, cache.retainedLayouts());
    }

    @Test
    void equalButDistinctPropertiesOrValuesRetainTheirExactObjects() {
        var cache = new NeighbourTableFactory.LayoutCache(8);
        var row1 = new Key("same row");
        var row2 = new Key("same row");
        var column1 = new Key("same column");
        var column2 = new Key("same column");
        var first = HashBasedTable.<Key, Key, Object>create();
        first.put(row1, column1, new Object());
        var second = HashBasedTable.<Key, Key, Object>create();
        second.put(row2, column2, new Object());
        assertIdentical(first, cache.create(first));
        assertIdentical(second, cache.create(second));
        assertEquals(2, cache.retainedLayouts());
    }

    @Test
    void distinctColumnSetsAndEncounterOrdersKeepOriginalLayout() {
        var cache = new NeighbourTableFactory.LayoutCache(8);
        var rows = List.of(new Key("a"), new Key("b"), new Key("c"));
        var columns = List.of(new Key("x"), new Key("y"), new Key("z"));
        for (int variant = 0; variant < 27; variant++) {
            var source = HashBasedTable.<Key, Key, Object>create();
            for (int row = 0; row < 3; row++) {
                int skipped = variant / (int) Math.pow(3, row) % 3;
                for (int column = 0; column < 3; column++) {
                    if (column != skipped) source.put(rows.get(row), columns.get(column), new Object());
                }
            }
            assertIdentical(source, cache.create(source));
            assertTrue(cache.retainedLayouts() <= 8);
        }
    }

    @Test
    void existingArrayTableNullCellsAndCustomTablesUseOriginalCopyBehavior() {
        var cache = new NeighbourTableFactory.LayoutCache(8);
        var array = ArrayTable.<String, String, Object>create(List.of("a", "b"), List.of("x", "y"));
        array.put("a", "x", new Object());
        assertIdentical(array, cache.create(array));
        var immutable = ImmutableTable.of("a", "x", new Object());
        assertIdentical(immutable, cache.create(immutable));
        var empty = HashBasedTable.<String, String, Object>create();
        Class<? extends RuntimeException> failure = null;
        ArrayTable<String, String, Object> expectedEmpty = null;
        try {
            expectedEmpty = ArrayTable.create(empty);
        } catch (RuntimeException error) {
            failure = error.getClass();
        }
        if (failure != null) assertThrows(failure, () -> cache.create(empty));
        else assertEquals(expectedEmpty, cache.create(empty));
        assertEquals(0, cache.retainedLayouts());
    }

    @Test
    void largeLayoutsAreNotRetained() {
        var cache = new NeighbourTableFactory.LayoutCache(8);
        var source = HashBasedTable.<Integer, Integer, Object>create();
        for (int i = 0; i < 65; i++) source.put(i, i, new Object());
        assertIdentical(source, cache.create(source));
        assertEquals(0, cache.retainedLayouts());
    }

    @Test
    void releaseClearsRetainedLayoutsAndDoesNotRepopulateOnLaterCalls() {
        var cache = new NeighbourTableFactory.LayoutCache(1);
        var source = HashBasedTable.<String, String, Object>create();
        source.put("a", "x", new Object());
        var saved = cache.create(source);
        assertEquals(1, cache.retainedLayouts());
        cache.release();
        assertEquals(0, cache.retainedLayouts());
        assertIdentical(source, saved);
        assertIdentical(source, cache.create(source));
        assertEquals(0, cache.retainedLayouts());
        cache.release();
    }

    @Test
    void excessiveLayoutsAreBoundedWithoutChangingPreviouslyReturnedTables() {
        var cache = new NeighbourTableFactory.LayoutCache(2);
        var returned = new ArrayList<ArrayTable<String, String, Object>>();
        var sources = new ArrayList<Table<String, String, Object>>();
        for (int i = 0; i < 20; i++) {
            var source = HashBasedTable.<String, String, Object>create();
            source.put("row" + i, "column" + i, new Object());
            sources.add(source);
            returned.add(cache.create(source));
            assertTrue(cache.retainedLayouts() <= 2);
        }
        for (int i = 0; i < returned.size(); i++) assertIdentical(sources.get(i), returned.get(i));
    }

    @Test
    void concurrentBuildersDoNotShareCellArrays() throws Exception {
        var cache = new NeighbourTableFactory.LayoutCache(8);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 100; i++) tasks.add(executor.submit(() -> {
                var source = HashBasedTable.<String, String, Object>create();
                source.put("a", "x", new Object());
                source.put("b", "y", new Object());
                var result = cache.create(source);
                assertIdentical(source, result);
                result.put("a", "y", new Object());
            }));
            for (var task : tasks) task.get();
        }
        assertEquals(1, cache.retainedLayouts());
    }
}

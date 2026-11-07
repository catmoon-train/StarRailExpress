package io.wifi.srepackcompat.startup;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class StateCombinationMapTest {
    private record Value(int value) {
        @Override public int hashCode() { return 7; }
    }
    private record State(Map<Integer, Value> values, int id) { }
    private static final List<Integer> PROPERTIES = List.of(0, 1, 2, 3, 4, 5);
    private static final List<Value> DOMAIN = IntStream.range(0, 4).mapToObj(Value::new).toList();

    private LinkedHashMap<Map<Integer, Value>, State> fixture(int size) {
        var ids = new ArrayList<>(IntStream.range(0, size).boxed().toList());
        Collections.shuffle(ids, new Random(40019));
        var original = new LinkedHashMap<Map<Integer, Value>, State>();
        for (int id : ids) {
            var values = new LinkedHashMap<Integer, Value>();
            // 属性与状态输入顺序都不与编码顺序一致。
            for (int property = 5; property >= 0; property--) values.put(property, new Value((id >>> (property * 2)) & 3));
            original.put(values, new State(values, id));
        }
        return original;
    }

    @Test
    void everyNeighbourLookupPreservesOriginalStateIdentityDespiteHashCollisions() {
        var original = fixture(4096);
        var indexed = StateCombinationMap.create(original, PROPERTIES, ignored -> DOMAIN, State::values);
        assertNotSame(original, indexed);
        for (State state : original.values()) {
            assertSame(state, indexed.get(new HashMap<>(state.values())));
            for (int property : PROPERTIES) {
                for (Value value : DOMAIN) {
                    var changed = new HashMap<>(state.values());
                    changed.put(property, value);
                    assertSame(original.get(changed), indexed.get(changed));
                }
            }
        }
    }

    @Test
    void sparseCombinationsRemainAbsent() {
        var original = fixture(150);
        var indexed = StateCombinationMap.create(original, PROPERTIES, ignored -> DOMAIN, State::values);
        var absent = new HashMap<>(original.values().iterator().next().values());
        absent.put(5, new Value(3));
        assertNull(original.get(absent));
        assertNull(indexed.get(absent));
        assertEquals(original.size(), indexed.size());
        assertEquals(new ArrayList<>(original.entrySet()), new ArrayList<>(indexed.entrySet()));
    }

    @Test
    void standardQueriesNeverCallOriginalGet() {
        var gets = new AtomicInteger();
        var original = new LinkedHashMap<Map<Integer, Value>, State>() {
            @Override public State get(Object key) { gets.incrementAndGet(); return super.get(key); }
        };
        original.putAll(fixture(256));
        var indexed = StateCombinationMap.create(original, PROPERTIES, ignored -> DOMAIN, State::values);
        for (State state : original.values()) assertSame(state, indexed.get(state.values()));
        assertEquals(0, gets.get());
        assertNull(indexed.get("not a state key"));
        assertEquals(1, gets.get());
    }

    @Test
    void invalidKeysKeepOriginalLookupSemantics() {
        var original = fixture(256);
        var indexed = StateCombinationMap.create(original, PROPERTIES, ignored -> DOMAIN, State::values);
        assertNull(indexed.get(null));
        assertNull(indexed.get(Map.of()));
        var unknownValue = new HashMap<>(original.values().iterator().next().values());
        unknownValue.put(0, new Value(99));
        assertNull(indexed.get(unknownValue));
        var unknownProperty = new HashMap<>(unknownValue);
        unknownProperty.remove(0);
        unknownProperty.put(100, new Value(0));
        assertNull(indexed.get(unknownProperty));
    }

    @Test
    void smallDefinitionsAndEmptyOrDuplicateDomainsUseOriginalMap() {
        var small = fixture(64);
        assertSame(small, StateCombinationMap.create(small, PROPERTIES, ignored -> DOMAIN, State::values));
        var original = fixture(256);
        assertSame(original, StateCombinationMap.create(original, PROPERTIES, ignored -> List.<Value>of(), State::values));
        assertSame(original, StateCombinationMap.create(original, PROPERTIES,
                ignored -> List.of(new Value(0), new Value(0)), State::values));
        assertSame(original, StateCombinationMap.create(original, List.of(0, 0), ignored -> DOMAIN, State::values));
    }

    @Test
    void indexBudgetAndIntegerOverflowUseOriginalMap() {
        var original = fixture(256);
        var many = IntStream.range(0, 32).boxed().toList();
        assertSame(original, StateCombinationMap.create(original, many, ignored -> DOMAIN, State::values));
        var tooMany = IntStream.range(0, 33).boxed().toList();
        assertSame(original, StateCombinationMap.create(original, tooMany, ignored -> DOMAIN, State::values));
    }

    @Test
    void incompatibleStateValuesAndDuplicateCombinationsUseOriginalMap() {
        var original = fixture(256);
        assertSame(original, StateCombinationMap.create(original, PROPERTIES,
                ignored -> DOMAIN.subList(0, 2), State::values));
        assertSame(original, StateCombinationMap.create(original, PROPERTIES,
                ignored -> DOMAIN, ignored -> original.values().iterator().next().values()));
        original.put(Map.of(), null);
        assertSame(original, StateCombinationMap.create(original, PROPERTIES, ignored -> DOMAIN, State::values));
    }
}

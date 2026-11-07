package io.wifi.srepackcompat.startup;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class StateValueIndexTest {
    @Test
    void matchesBruteForceForLargeStateSpaceAndArbitraryEncounterOrder() {
        var states = new ArrayList<>(IntStream.range(0, 4096).boxed().toList());
        var random = new Random(724831);
        Collections.shuffle(states, random);
        var index = new StateValueIndex<Integer, Integer, Integer>(states, StateValueIndexTest::value);
        for (int query = 0; query < 250; query++) {
            var constraints = new HashMap<Integer, Integer>();
            for (int property = 0; property < 6; property++) {
                if (random.nextBoolean()) constraints.put(property, random.nextInt(5));
            }
            assertEquals(scan(states, constraints), index.select(constraints), constraints.toString());
        }
    }

    @Test
    void eachPropertyIsReadOnlyOnceRatherThanOncePerVariant() {
        var reads = new AtomicInteger();
        List<Integer> states = IntStream.range(0, 4096).boxed().toList();
        var index = new StateValueIndex<Integer, Integer, Integer>(states, (state, property) -> {
            reads.incrementAndGet();
            return value(state, property);
        });
        for (int variant = 0; variant < 4096; variant++) {
            var constraints = new HashMap<Integer, Integer>();
            for (int property = 0; property < 6; property++) constraints.put(property, value(variant, property));
            assertEquals(List.of(variant), index.select(constraints));
        }
        assertEquals(4096 * 6, reads.get());
    }

    @Test
    void intersectionsDoNotModifyCachedPropertySetsOrEarlierResults() {
        List<Integer> states = IntStream.range(0, 64).boxed().toList();
        var index = new StateValueIndex<Integer, Integer, Integer>(states, StateValueIndexTest::value);
        List<Integer> earlier = index.select(Map.of(0, 2));
        index.select(Map.of(0, 2, 1, 3));
        index.select(Map.of(0, 1, 1, 0));
        assertEquals(scan(states, Map.of(0, 2)), earlier);
        assertEquals(earlier, index.select(Map.of(0, 2)));
        assertThrows(UnsupportedOperationException.class, () -> earlier.add(3));
    }

    @Test
    void comparesValuesByEqualityAndKeepsDuplicateStatesInOriginalOrder() {
        String first = new String("same");
        String second = new String("same");
        var index = new StateValueIndex<String, String, String>(List.of(first, "other", second, first),
                (state, ignored) -> state);
        List<String> result = index.select(Map.of("property", new String("same")));
        assertEquals(3, result.size());
        assertSame(first, result.get(0));
        assertSame(second, result.get(1));
        assertSame(first, result.get(2));
    }

    @Test
    void emptyConstraintsAndEmptyStateSetsRemainUnmodifiable() {
        var source = new ArrayList<>(List.of(2, 1, 3));
        var index = new StateValueIndex<Integer, Integer, Integer>(source, StateValueIndexTest::value);
        source.clear();
        assertEquals(List.of(2, 1, 3), index.select(Map.of()));
        assertThrows(UnsupportedOperationException.class, () -> index.select(Map.of()).clear());
        var empty = new StateValueIndex<Integer, Integer, Integer>(List.of(), StateValueIndexTest::value);
        assertEquals(List.of(), empty.select(Map.of(0, 1)));
    }

    @Test
    void queryOrderAndLaterConstraintChangesDoNotAffectIndex() {
        List<Integer> states = IntStream.range(0, 64).boxed().toList();
        var index = new StateValueIndex<Integer, Integer, Integer>(states, StateValueIndexTest::value);
        var constraints = new LinkedHashMap<Integer, Integer>();
        constraints.put(1, 2);
        constraints.put(0, 3);
        List<Integer> result = index.select(constraints);
        constraints.clear();
        assertEquals(result, index.select(Map.of(0, 3, 1, 2)));
        assertEquals(List.of(), index.select(Map.of(0, 99)));
    }

    @Test
    void concurrentQueriesShareIndexesWithoutChangingResults() throws Exception {
        List<Integer> states = IntStream.range(0, 4096).boxed().toList();
        var index = new StateValueIndex<Integer, Integer, Integer>(states, StateValueIndexTest::value);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = IntStream.range(0, 200).mapToObj(i -> executor.submit(() -> {
                var constraints = Map.of(0, i % 4, 1, (i / 4) % 4);
                assertEquals(scan(states, constraints), index.select(constraints));
            })).toList();
            for (var task : tasks) task.get();
        }
    }

    private static int value(int state, int property) {
        return (state >>> (property * 2)) & 3;
    }

    private static List<Integer> scan(List<Integer> states, Map<Integer, Integer> constraints) {
        return states.stream().filter(state -> constraints.entrySet().stream()
                .allMatch(entry -> Objects.equals(value(state, entry.getKey()), entry.getValue()))).toList();
    }
}

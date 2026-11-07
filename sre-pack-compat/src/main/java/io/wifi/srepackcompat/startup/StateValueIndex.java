package io.wifi.srepackcompat.startup;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/** 不改变状态顺序的属性值倒排索引；每个用到的属性只扫描一次完整状态集。 */
public final class StateValueIndex<S, P, V> {
    private final List<S> states;
    private final BiFunction<S, P, V> valueReader;
    private final Map<P, Map<V, BitSet>> properties = new HashMap<>();

    public StateValueIndex(List<S> states, BiFunction<S, P, V> valueReader) {
        this.states = List.copyOf(states);
        this.valueReader = valueReader;
    }

    public synchronized List<S> select(Map<P, V> constraints) {
        if (constraints.isEmpty()) {
            return states;
        }
        BitSet matches = null;
        for (var entry : constraints.entrySet()) {
            var values = properties.computeIfAbsent(entry.getKey(), this::indexProperty);
            BitSet allowed = values.get(entry.getValue());
            if (allowed == null) {
                return List.of();
            }
            if (matches == null) {
                matches = (BitSet) allowed.clone();
            } else {
                matches.and(allowed);
            }
            if (matches.isEmpty()) {
                return List.of();
            }
        }
        var result = new ArrayList<S>(matches.cardinality());
        for (int i = matches.nextSetBit(0); i >= 0; i = matches.nextSetBit(i + 1)) {
            result.add(states.get(i));
        }
        return List.copyOf(result);
    }

    private Map<V, BitSet> indexProperty(P property) {
        var values = new HashMap<V, BitSet>();
        for (int i = 0; i < states.size(); i++) {
            V value = valueReader.apply(states.get(i), property);
            values.computeIfAbsent(value, ignored -> new BitSet()).set(i);
        }
        return values;
    }
}

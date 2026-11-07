package io.wifi.srepackcompat.startup;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 构建期属性组合查找表：混合进位编码避免 Map 键的累加哈希发生大量碰撞。 */
public final class StateCombinationMap<P, V, S> extends AbstractMap<Map<P, V>, S> {
    private static final int MAX_SLOTS = 262144;
    private record Dimension<P, V>(P property, Map<V, Integer> ordinals, int stride) { }
    private final Map<Map<P, V>, S> original;
    private final List<Dimension<P, V>> dimensions;
    private final Object[] slots;

    private StateCombinationMap(Map<Map<P, V>, S> original, List<Dimension<P, V>> dimensions, int size) {
        this.original = original;
        this.dimensions = List.copyOf(dimensions);
        this.slots = new Object[size];
    }

    /** 不假设状态列表的排列方式；不能无歧义建立有界索引时直接返回原容器。 */
    public static <P, V, S> Map<Map<P, V>, S> create(Map<Map<P, V>, S> original, List<P> properties,
            Function<P, ? extends Collection<? extends V>> possibleValues, Function<S, Map<P, V>> stateValues) {
        if (original.size() <= 64 || original.size() > MAX_SLOTS || properties.size() > 32
                || new HashSet<>(properties).size() != properties.size()) {
            return original;
        }
        int size = 1;
        var dimensions = new ArrayList<Dimension<P, V>>(properties.size());
        for (P property : properties) {
            var ordinals = new HashMap<V, Integer>();
            for (V value : possibleValues.apply(property)) {
                if (value == null || ordinals.putIfAbsent(value, ordinals.size()) != null) {
                    return original;
                }
            }
            if (ordinals.isEmpty() || size > MAX_SLOTS / ordinals.size()) {
                return original;
            }
            dimensions.add(new Dimension<>(property, ordinals, size));
            size *= ordinals.size();
        }
        var indexed = new StateCombinationMap<P, V, S>(original, dimensions, size);
        for (S state : original.values()) {
            if (state == null) return original;
            int key = indexed.encode(stateValues.apply(state));
            if (key < 0 || indexed.slots[key] != null) return original;
            indexed.slots[key] = state;
        }
        return indexed;
    }

    private int encode(Map<?, ?> values) {
        if (values.size() != dimensions.size()) return -1;
        int code = 0;
        for (var dimension : dimensions) {
            Integer ordinal = dimension.ordinals().get(values.get(dimension.property()));
            if (ordinal == null) return -1;
            code += ordinal * dimension.stride();
        }
        return code;
    }

    @Override
    @SuppressWarnings("unchecked")
    public S get(Object key) {
        if (key instanceof Map<?, ?> values) {
            int code = encode(values);
            if (code >= 0) return (S) slots[code];
        }
        // 非法或非标准键继续保留原 Map 的查询语义。
        return original.get(key);
    }

    @Override
    public int size() {
        return original.size();
    }

    @Override
    public Set<Entry<Map<P, V>, S>> entrySet() {
        return original.entrySet();
    }
}

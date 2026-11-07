package io.wifi.srepackcompat.startup;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 复用原版 ArrayTable 的不可变行列索引；每个返回表仍拥有独立的单元格数组。 */
public final class NeighbourTableFactory {
    private static final LayoutCache STARTUP = new LayoutCache(256);

    private NeighbourTableFactory() { }

    public static <R, C, V> ArrayTable<R, C, V> create(Table<R, C, V> source) {
        return STARTUP.create(source);
    }

    public static void release() {
        STARTUP.release();
    }

    /** 缓存仅含全空模板，不保留任何状态对象或源表。 */
    static final class LayoutCache {
        private record Layout(List<?> rows, List<?> columns) {
            @Override
            public boolean equals(Object other) {
                return other instanceof Layout layout && sameKeys(rows, layout.rows) && sameKeys(columns, layout.columns);
            }

            private static boolean sameKeys(List<?> first, List<?> second) {
                if (first.size() != second.size()) return false;
                for (int i = 0; i < first.size(); i++) if (first.get(i) != second.get(i)) return false;
                return true;
            }

            @Override
            public int hashCode() {
                int hash = 1;
                for (Object row : rows) hash = 31 * hash + System.identityHashCode(row);
                for (Object column : columns) hash = 31 * hash + System.identityHashCode(column);
                return hash;
            }
        }
        private final Map<Layout, ArrayTable<?, ?, ?>> templates;
        private boolean active = true;

        LayoutCache(int limit) {
            templates = new LinkedHashMap<>(16, .75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Layout, ArrayTable<?, ?, ?>> eldest) {
                    return size() > limit;
                }
            };
        }

        <R, C, V> ArrayTable<R, C, V> create(Table<R, C, V> source) {
            // ArrayTable 自身可含 null 单元格，自定义 Table 也可能有特殊迭代语义，均交回原版。
            if (!(source instanceof HashBasedTable<?, ?, ?>) || source.isEmpty()) {
                return ArrayTable.create(source);
            }
            var rows = new ArrayList<R>();
            source.rowKeySet().forEach(rows::add);
            var columns = new ArrayList<C>();
            source.columnKeySet().forEach(columns::add);
            if ((long) rows.size() * columns.size() > 4096) return ArrayTable.create(source);
            var layout = new Layout(List.copyOf(rows), List.copyOf(columns));
            ArrayTable<R, C, V> template;
            synchronized (templates) {
                if (!active) return ArrayTable.create(source);
                @SuppressWarnings("unchecked")
                var existing = (ArrayTable<R, C, V>) templates.get(layout);
                if (existing == null) {
                    existing = ArrayTable.create(rows, columns);
                    templates.put(layout, existing);
                }
                template = existing;
            }
            // Guava 官方拷贝构造复用不可变键索引，但逐行复制数组；不共享可写单元格。
            ArrayTable<R, C, V> result = ArrayTable.create(template);
            result.putAll(source);
            return result;
        }

        void release() {
            synchronized (templates) {
                active = false;
                templates.clear();
            }
        }

        int retainedLayouts() {
            synchronized (templates) {
                return templates.size();
            }
        }
    }
}

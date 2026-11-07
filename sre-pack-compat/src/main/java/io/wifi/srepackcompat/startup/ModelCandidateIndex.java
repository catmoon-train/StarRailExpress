package io.wifi.srepackcompat.startup;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 仅缩小原回调的候选 entrySet，不构造模型；相撞时保留原遍历顺序的首个类型。 */
public final class ModelCandidateIndex<B> {
    private static final int MAX_BASES = 4096;
    private static final int MAX_KEYS = 8192;
    private final Map<String, Set<B>> source;
    private final Map<String, Map<String, Set<Map.Entry<String, Set<B>>>>> topLevel = new HashMap<>();
    private final Map<String, Map<String, Set<Map.Entry<String, Set<B>>>>> beforeBake = new HashMap<>();
    private int keys;

    private ModelCandidateIndex(Map<String, Set<B>> source) { this.source = source; }

    public static <B> ModelCandidateIndex<B> create(Map<String, Set<B>> bases,
            Map<String, ? extends Iterable<String>> stages, Function<B, String> namespace, Function<B, String> path) {
        ModelCandidateIndex<B> result = new ModelCandidateIndex<>(bases);
        int count = 0;
        for (var entry : bases.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) return null;
            Set<Map.Entry<String, Set<B>>> candidate = Collections.singleton(entry);
            for (B base : entry.getValue()) {
                if (++count > MAX_BASES || base == null) return null;
                String ns = namespace.apply(base), basePath = path.apply(base);
                if (ns == null || basePath == null) return null;
                // onLoad 对空后缀始终接受，即使该类型的阶段表没有空串。
                result.add(result.topLevel, ns, basePath, candidate);
                Iterable<String> suffixes = stages.get(entry.getKey());
                if (suffixes == null) continue;
                for (String suffix : suffixes) {
                    if (suffix == null) continue;
                    result.add(result.topLevel, ns, basePath + suffix, candidate);
                    result.add(result.beforeBake, ns, basePath + suffix, candidate);
                    if (result.keys > MAX_KEYS) return null;
                }
            }
        }
        return result.keys <= MAX_KEYS ? result : null;
    }

    private void add(Map<String, Map<String, Set<Map.Entry<String, Set<B>>>>> table, String namespace,
                     String path, Set<Map.Entry<String, Set<B>>> candidate) {
        if (table.computeIfAbsent(namespace, ignored -> new HashMap<>()).putIfAbsent(path, candidate) == null) keys++;
    }

    public boolean isSource(Map<String, Set<B>> bases) { return source == bases; }
    public int keyCount() { return keys; }

    public Set<Map.Entry<String, Set<B>>> candidates(String namespace, String path, boolean baking) {
        if (namespace == null || path == null) return Set.of();
        boolean item = path.startsWith("item/");
        if (baking && !item) return Set.of();
        if (item) path = path.substring(5);
        var paths = (baking ? beforeBake : topLevel).get(namespace);
        if (paths == null) return Set.of();
        var result = paths.get(path);
        return result == null ? Set.of() : result;
    }
}

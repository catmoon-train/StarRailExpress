package io.wifi.srepackcompat.startup;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ModelCandidateIndexTest {
    record Base(String namespace, String path) { }
    record Match(String type, Base base, String suffix) { }

    private static ModelCandidateIndex<Base> index(Map<String, Set<Base>> bases, Map<String, List<String>> stages) {
        return ModelCandidateIndex.create(bases, stages, Base::namespace, Base::path);
    }

    // 对照核心原回调的遍历与条件；同样用于筛选后的 entrySet。
    private static Match original(Iterable<Map.Entry<String, Set<Base>>> entries, Map<String, List<String>> stages,
                                  String namespace, String path, boolean baking) {
        if (namespace == null || path == null || baking && !path.startsWith("item/")) return null;
        if (path.startsWith("item/")) path = path.substring(5);
        for (var entry : entries) {
            List<String> suffixes = stages.get(entry.getKey());
            if (baking && suffixes == null) continue;
            for (Base base : entry.getValue()) {
                if (!base.namespace.equals(namespace) || !path.startsWith(base.path)) continue;
                String suffix = path.substring(base.path.length());
                if ((baking || !suffix.isEmpty()) && (suffixes == null || !suffixes.contains(suffix))) continue;
                return new Match(entry.getKey(), base, suffix);
            }
        }
        return null;
    }

    private static void equivalent(ModelCandidateIndex<Base> index, Map<String, Set<Base>> bases,
                                   Map<String, List<String>> stages, String namespace, String path, boolean baking) {
        Match expected = original(bases.entrySet(), stages, namespace, path, baking);
        Match actual = original(index.candidates(namespace, path, baking), stages, namespace, path, baking);
        assertEquals(expected, actual, namespace + ':' + path + " baking=" + baking);
        if (expected != null) assertSame(expected.base, actual.base);
    }

    @Test
    void namespacesAndOptionalItemPrefixStayDistinct() {
        var bases = Map.of("tool", Set.of(new Base("minecraft", "key"), new Base("other", "key")));
        var stages = Map.<String, List<String>>of(); var index = index(bases, stages);
        for (String ns : List.of("minecraft", "other", "missing")) {
            for (String path : List.of("key", "item/key", "keys", "item/key_extra")) equivalent(index, bases, stages, ns, path, false);
        }
        assertTrue(index.candidates("missing", "key", false).isEmpty());
    }

    @Test
    void sharedSkinTypeRetainsEntireOriginalOrderedSet() {
        Set<Base> shared = new LinkedHashSet<>(List.of(new Base("sre", "key"), new Base("nr", "master_key_p")));
        var bases = Map.of("key", shared); var index = index(bases, Map.of());
        assertSame(shared, index.candidates("nr", "master_key_p", false).iterator().next().getValue());
        equivalent(index, bases, Map.of(), "nr", "master_key_p", false);
        assertEquals(2, shared.size());
    }

    @Test
    void prefixCollisionsKeepFirstTypeAndFirstBaseNotLongestPrefix() {
        Base bow = new Base("minecraft", "bow"), stage = new Base("minecraft", "bow_pulling_0");
        Map<String, Set<Base>> bases = new LinkedHashMap<>();
        bases.put("bow", new LinkedHashSet<>(List.of(bow, stage)));
        bases.put("other", Set.of(stage));
        var stages = Map.of("bow", List.of("", "_pulling_0"));
        var index = index(bases, stages);
        Match match = original(index.candidates("minecraft", "bow_pulling_0", false), stages, "minecraft", "bow_pulling_0", false);
        assertSame(bow, match.base);
        equivalent(index, bases, stages, "minecraft", "bow_pulling_0", false);
        Map<String, Set<Base>> reversed = new LinkedHashMap<>(); reversed.put("other", Set.of(stage)); reversed.put("bow", Set.of(bow));
        var reverseIndex = index(reversed, stages);
        assertEquals("other", original(reverseIndex.candidates("minecraft", "bow_pulling_0", false), stages,
                "minecraft", "bow_pulling_0", false).type);
    }

    @Test
    void beforeBakeRequiresItemPrefixAndExplicitStageIncludingEmptyStage() {
        var bases = Map.of("bow", Set.of(new Base("minecraft", "bow")), "key", Set.of(new Base("sre", "key")));
        var stages = Map.of("bow", List.of("_pulling_0", "_pulling_1"));
        var index = index(bases, stages);
        assertFalse(index.candidates("minecraft", "bow", false).isEmpty());
        assertTrue(index.candidates("minecraft", "item/bow", true).isEmpty());
        assertTrue(index.candidates("minecraft", "bow_pulling_0", true).isEmpty());
        assertFalse(index.candidates("minecraft", "item/bow_pulling_0", true).isEmpty());
        assertTrue(index.candidates("sre", "item/key", true).isEmpty());
        equivalent(index, bases, stages, "minecraft", "item/bow_pulling_0", true);
    }

    @Test
    void removesOnlyOneItemPrefixAndSupportsSlashInBasePath() {
        var bases = Map.of("nested", Set.of(new Base("sre", "item/nested/key")));
        var stages = Map.of("nested", List.of("", "_ready")); var index = index(bases, stages);
        for (String path : List.of("item/item/nested/key", "item/item/nested/key_ready", "item/nested/key")) {
            equivalent(index, bases, stages, "sre", path, false);
            equivalent(index, bases, stages, "sre", path, true);
        }
    }

    @Test
    void duplicateStagesNullAndEmptyLookupsDoNotChangeResults() {
        var bases = Map.of("tool", Set.of(new Base("sre", "tool")));
        Map<String, List<String>> stages = Map.of("tool", java.util.Arrays.asList("", "_ready", "_ready", null));
        var index = index(bases, stages);
        assertEquals(4, index.keyCount());
        assertTrue(index.candidates(null, "tool", false).isEmpty());
        assertTrue(index.candidates("sre", null, false).isEmpty());
        equivalent(index, bases, stages, "sre", "item/tool_ready", true);
        assertTrue(index(Map.of(), Map.of()).candidates("sre", "tool", false).isEmpty());
    }

    @Test
    void boundedIndexesFallBackWithoutChangingInputs() {
        Set<Base> many = new LinkedHashSet<>();
        for (int i = 0; i < 4097; i++) many.add(new Base("test", "base_" + i));
        var bases = Map.of("many", many);
        assertNull(index(bases, Map.of()));
        assertEquals(4097, many.size());
        List<String> stages = new ArrayList<>();
        for (int i = 0; i < 5000; i++) stages.add("_" + i);
        assertNull(index(Map.of("staged", Set.of(new Base("test", "base"))), Map.of("staged", stages)));
        assertEquals(5000, stages.size());
    }

    @Test
    void sourceIdentityAndRebuildAfterRegistrationChangesAreExplicit() {
        Map<String, Set<Base>> bases = new LinkedHashMap<>(); bases.put("key", new LinkedHashSet<>(Set.of(new Base("sre", "key"))));
        var first = index(bases, Map.of());
        assertTrue(first.isSource(bases));
        assertFalse(first.isSource(new LinkedHashMap<>(bases)));
        bases.get("key").add(new Base("nr", "master_key_p"));
        var second = index(bases, Map.of());
        equivalent(second, bases, Map.of(), "nr", "master_key_p", false);
        assertFalse(second.candidates("nr", "master_key_p", false).isEmpty());
    }

    @Test
    void queryingDoesNotRescanBaseMetadataOrAllocateCandidateSets() {
        AtomicInteger reads = new AtomicInteger();
        var bases = Map.of("tool", Set.of(new Base("sre", "tool")));
        var index = ModelCandidateIndex.create(bases, Map.of(), b -> { reads.incrementAndGet(); return b.namespace; },
                b -> { reads.incrementAndGet(); return b.path; });
        int builtReads = reads.get();
        var candidate = index.candidates("sre", "tool", false);
        for (int i = 0; i < 10000; i++) {
            assertSame(candidate, index.candidates("sre", "tool", false));
            assertTrue(index.candidates("sre", "missing", false).isEmpty());
        }
        assertEquals(builtReads, reads.get());
        assertThrows(UnsupportedOperationException.class, () -> candidate.clear());
    }

    @Test
    void exhaustivelyMatchesOriginalAcrossOrdersNamespacesStagesAndOverlaps() {
        List<String> paths = List.of("bow", "bow_pulling_0", "bow_pulling", "key", "key_ready", "item/key", "folder/key");
        var stages = Map.of("type0", List.of("", "_pulling_0", "_ready"), "type2", List.of("_0", "_ready"));
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        Random random = new Random(7481);
        for (int round = 0; round < 24; round++) {
            Collections.shuffle(order, random);
            Map<String, Set<Base>> bases = new LinkedHashMap<>();
            for (int type : order) {
                List<Base> entries = new ArrayList<>();
                for (int n = 0; n < 3; n++) for (String path : paths) entries.add(new Base("ns" + n, path));
                Collections.shuffle(entries, random);
                bases.put("type" + type, new LinkedHashSet<>(entries));
            }
            var index = index(bases, stages);
            for (String ns : List.of("ns0", "ns1", "ns2", "missing")) for (String path : paths) {
                for (String prefix : List.of("", "item/", "item/item/")) for (String suffix : List.of("", "_0", "_ready", "_pulling_0", "_unknown")) {
                    equivalent(index, bases, stages, ns, prefix + path + suffix, false);
                    equivalent(index, bases, stages, ns, prefix + path + suffix, true);
                }
            }
        }
    }

    @Test
    void parallelReadersKeepPublishedCandidateIdentity() throws Exception {
        var bases = Map.of("key", Set.of(new Base("sre", "key")));
        var index = index(bases, Map.of()); var expected = index.candidates("sre", "key", false);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int t = 0; t < 16; t++) tasks.add(() -> {
                for (int i = 0; i < 4096; i++) if (index.candidates("sre", "key", false) != expected) return false;
                return true;
            });
            for (var result : executor.invokeAll(tasks)) assertTrue(result.get());
        }
    }

    @Test
    void unsupportedNullMetadataFallsBack() {
        Map<String, Set<Base>> bases = new LinkedHashMap<>(); bases.put("bad", null);
        assertNull(index(bases, Map.of()));
        bases.put("bad", new LinkedHashSet<>(java.util.Arrays.asList((Base) null)));
        assertNull(index(bases, Map.of()));
        assertNull(index(Map.of("bad", Set.of(new Base(null, "key"))), Map.of()));
    }
}

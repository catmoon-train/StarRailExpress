package io.wifi.srepackcompat.startup;

import net.fabricmc.loader.impl.game.patch.GameTransformer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StartupClassByteCacheTest {
    private static class Source implements ClassBytesSource {
        final Map<String, byte[]> data = new ConcurrentHashMap<>();
        final AtomicInteger reads = new AtomicInteger();
        volatile boolean current = true;
        volatile boolean closed;
        @Override public byte[] read(String name) throws IOException {
            reads.incrementAndGet();
            byte[] value = data.get(name);
            return value == null ? null : value.clone();
        }
        @Override public boolean isCurrent() { return current; }
        @Override public void close() { closed = true; }
    }

    private StartupClassByteCache cache(Map<String, byte[]> patches, ClassBytesSource source,
                                        long maxBytes, int maxEntries, AtomicLong clock) {
        return new StartupClassByteCache(patches, source, maxBytes, maxEntries, 1000, clock::get);
    }

    @Test
    void originalPatchesAlwaysWinAndDoNotBecomeRawEntries() {
        Source source = new Source();
        source.data.put("a.Raw", new byte[]{1, 2});
        byte[] patch = {9, 8};
        Map<String, byte[]> patches = new HashMap<>(Map.of("a.Patched", patch));
        var cache = cache(patches, source, 32, 4, new AtomicLong());
        assertSame(patch, cache.get("a.Patched"));
        assertEquals(0, source.reads.get());
        assertArrayEquals(new byte[]{1, 2}, cache.get("a.Raw"));
        assertFalse(cache.containsKey("a.Raw"));
        assertEquals(1, cache.size());
        assertEquals(patches.entrySet(), cache.entrySet());
        patches.put("a.Raw", patch);
        assertSame(patch, cache.get("a.Raw"));
    }

    @Test
    void everyConsumerGetsIndependentRawBytes() {
        Source source = new Source();
        source.data.put("a.Raw", new byte[]{1, 2});
        var cache = cache(new HashMap<>(), source, 32, 4, new AtomicLong());
        byte[] first = cache.get("a.Raw");
        first[0] = 99;
        byte[] second = cache.get("a.Raw");
        second[1] = 99;
        assertArrayEquals(new byte[]{1, 2}, cache.get("a.Raw"));
        assertEquals(1, source.reads.get());
        assertEquals(2, cache.stats().hits());
    }

    @Test
    void missingClassIsNotCached() {
        Source source = new Source();
        var cache = cache(new HashMap<>(), source, 32, 4, new AtomicLong());
        assertNull(cache.get("later.Added"));
        source.data.put("later.Added", new byte[]{3});
        assertArrayEquals(new byte[]{3}, cache.get("later.Added"));
        assertEquals(2, source.reads.get());
    }

    @Test
    void lruEvictsWithinByteAndEntryBudgets() {
        Source source = new Source();
        for (String name : new String[]{"a", "b", "c"}) source.data.put(name, new byte[]{1, 2});
        var cache = cache(new HashMap<>(), source, 4, 2, new AtomicLong());
        cache.get("a"); cache.get("b"); cache.get("a"); cache.get("c");
        assertEquals(1, cache.stats().evictions());
        assertEquals(4, cache.stats().retainedBytes());
        cache.get("b");
        assertEquals(4, source.reads.get());
        assertTrue(cache.stats().peakBytes() <= 4);
        assertTrue(cache.stats().retainedEntries() <= 2);
        var tinyEntries = cache(new HashMap<>(), source, 64, 1, new AtomicLong());
        tinyEntries.get("a"); tinyEntries.get("b");
        assertEquals(1, tinyEntries.stats().retainedEntries());
    }

    @Test
    void oversizedClassIsReadButNotRetained() {
        Source source = new Source();
        source.data.put("huge", new byte[9]);
        var cache = cache(new HashMap<>(), source, 8, 4, new AtomicLong());
        assertEquals(9, cache.get("huge").length);
        assertEquals(9, cache.get("huge").length);
        assertEquals(0, cache.stats().retainedBytes());
        assertEquals(2, source.reads.get());
    }

    @Test
    void classpathChangeInvalidatesPositiveEntriesAndReleasesSource() {
        Source source = new Source();
        source.data.put("a", new byte[]{1});
        var cache = cache(new HashMap<>(), source, 8, 4, new AtomicLong());
        cache.get("a"); source.current = false;
        assertNull(cache.get("a"));
        assertTrue(source.closed);
        assertEquals("classpath-changed", cache.stats().stopped());
        assertEquals(0, cache.stats().retainedBytes());
    }

    @Test
    void timeoutAndFinishKeepOriginalEntrypointPatches() {
        Source source = new Source();
        source.data.put("a", new byte[]{1});
        AtomicLong clock = new AtomicLong();
        byte[] patched = {7};
        var cache = cache(new HashMap<>(Map.of("patched", patched)), source, 8, 4, clock);
        cache.get("a"); clock.set(1000);
        assertNull(cache.get("a"));
        assertSame(patched, cache.get("patched"));
        assertEquals("timeout", cache.stop("later-finish").stopped());
        assertEquals(0, cache.stats().retainedBytes());
        assertTrue(source.closed);
    }

    @Test
    void readFailureFallsBackAndCanBeRetried() {
        AtomicInteger calls = new AtomicInteger();
        Source source = new Source() {
            @Override public byte[] read(String name) throws IOException {
                if (calls.getAndIncrement() == 0) throw new IOException("unreadable");
                return new byte[]{5};
            }
        };
        var cache = cache(new HashMap<>(), source, 8, 4, new AtomicLong());
        assertNull(cache.get("a"));
        assertArrayEquals(new byte[]{5}, cache.get("a"));
        assertEquals(1, cache.stats().failures());
    }

    @Test
    void recursiveResourceLookupUsesOriginalFallback() {
        AtomicReference<StartupClassByteCache> ref = new AtomicReference<>();
        Source source = new Source() {
            @Override public byte[] read(String name) {
                assertNull(ref.get().get("recursive"));
                return new byte[]{6};
            }
        };
        var cache = cache(new HashMap<>(), source, 8, 4, new AtomicLong());
        ref.set(cache);
        assertArrayEquals(new byte[]{6}, cache.get("a"));
    }

    @Test
    void concurrentConsumersCannotMutateEachOthersResults() throws Exception {
        Source source = new Source();
        source.data.put("a", new byte[128]);
        var cache = cache(new HashMap<>(), source, 1024, 4, new AtomicLong());
        cache.get("a");
        try (var executor = Executors.newFixedThreadPool(8)) {
            var jobs = java.util.stream.IntStream.range(0, 256).mapToObj(i -> executor.submit(() -> {
                byte[] value = cache.get("a");
                assertTrue(Arrays.equals(value, new byte[128]));
                Arrays.fill(value, (byte) 9);
            })).toList();
            for (var job : jobs) job.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, source.reads.get());
        assertEquals(256, cache.stats().hits());
    }

    @Test
    void stoppingDuringReadCannotRepopulateReleasedCache() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Source source = new Source() {
            @Override public byte[] read(String name) throws IOException {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("timeout");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                return new byte[]{1};
            }
        };
        var cache = cache(new HashMap<>(), source, 8, 4, new AtomicLong());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> cache.get("a"));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                cache.stop("ready");
            } finally { release.countDown(); }
            assertNull(result.get(5, TimeUnit.SECONDS));
        }
        assertEquals(0, cache.stats().retainedBytes());
        assertEquals(0, cache.stats().retainedEntries());
    }

    @Test
    void actualLoaderTransformerUsesOriginalPatchFirstAndRestoresCleanly() throws Exception {
        GameTransformer transformer = new GameTransformer();
        var field = GameTransformer.class.getDeclaredField("patchedClasses");
        field.setAccessible(true);
        byte[] patched = {11};
        Map<String, byte[]> original = new HashMap<>(Map.of("patched", patched));
        Source source = new Source();
        source.data.put("raw", new byte[]{12});
        field.set(transformer, cache(original, source, 8, 4, new AtomicLong()));
        assertSame(patched, transformer.transform("patched"));
        assertArrayEquals(new byte[]{12}, transformer.transform("raw"));
        assertArrayEquals(new byte[]{12}, transformer.transform("raw"));
        assertEquals(1, source.reads.get());
        field.set(transformer, original);
        assertNull(transformer.transform("raw"));
        assertSame(patched, transformer.transform("patched"));
    }
}

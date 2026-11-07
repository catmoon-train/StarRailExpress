package io.wifi.srepackcompat.startup;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * 只用于已完成入口定位的 GameTransformer.transform 读穿入口。
 * 原补丁表和原始字节缓存分离：不向补丁表写入原始类，不改变 Fabric/Mixin 后续转换。
 */
final class StartupClassByteCache extends AbstractMap<String, byte[]> {
    record Stats(long reads, long hits, long bypasses, long failures, long evictions,
                 long peakBytes, int retainedEntries, long retainedBytes, String stopped) { }

    private final Map<String, byte[]> patches;
    private final LinkedHashMap<String, byte[]> bytes = new LinkedHashMap<>(256, .75F, true);
    private final ThreadLocal<Boolean> reading = new ThreadLocal<>();
    private final long maxBytes;
    private final int maxEntries;
    private final long lifetimeNanos;
    private final LongSupplier clock;
    private final long started;
    private ClassBytesSource source;
    private long retainedBytes, peakBytes, reads, hits, bypasses, failures, evictions;
    private String stopped;

    StartupClassByteCache(Map<String, byte[]> patches, ClassBytesSource source,
                         long maxBytes, int maxEntries, long lifetimeNanos, LongSupplier clock) {
        if (maxBytes <= 0 || maxEntries <= 0 || lifetimeNanos <= 0) throw new IllegalArgumentException("Invalid budget");
        this.patches = patches;
        this.source = source;
        this.maxBytes = maxBytes;
        this.maxEntries = maxEntries;
        this.lifetimeNanos = lifetimeNanos;
        this.clock = clock;
        this.started = clock.getAsLong();
    }

    @Override
    public byte[] get(Object key) {
        byte[] patch = patches.get(key);
        if (patch != null || !(key instanceof String name)) return patch;
        if (Boolean.TRUE.equals(reading.get())) return null;
        reading.set(true);
        try {
            ClassBytesSource snapshot;
            synchronized (this) {
                if (source == null) return null;
                if (clock.getAsLong() - started >= lifetimeNanos) {
                    stop("timeout");
                    return null;
                }
                snapshot = source;
                if (!snapshot.isCurrent()) {
                    stop("classpath-changed");
                    return null;
                }
                byte[] cached = bytes.get(name);
                if (cached != null) {
                    hits++;
                    // ASM 或其他转换器即使原地改写，也不能污染下一次原始读取。
                    return cached.clone();
                }
            }
            byte[] raw = snapshot.read(name);
            synchronized (this) {
                if (source != snapshot || !snapshot.isCurrent()) {
                    if (source != null) stop("classpath-changed");
                    return null;
                }
                if (raw == null) {
                    bypasses++;
                    return null; // 不缓存不存在的类，父类路径和后加类仍由 Knot 判断。
                }
                reads++;
                if (raw.length <= maxBytes) {
                    byte[] previous = bytes.put(name, raw.clone());
                    if (previous != null) retainedBytes -= previous.length;
                    retainedBytes += raw.length;
                    var iterator = bytes.entrySet().iterator();
                    while (retainedBytes > maxBytes || bytes.size() > maxEntries) {
                        retainedBytes -= iterator.next().getValue().length;
                        iterator.remove();
                        evictions++;
                    }
                    peakBytes = Math.max(peakBytes, retainedBytes);
                }
                return raw;
            }
        } catch (IOException error) {
            synchronized (this) { failures++; }
            return null; // 原流程会重读并保留原来的异常传播，不能以缓存错误阻断启动。
        } catch (RuntimeException error) {
            synchronized (this) {
                failures++;
                stop("cache-error");
            }
            return null;
        } finally {
            reading.remove();
        }
    }

    synchronized Stats stop(String reason) {
        if (source != null) {
            stopped = reason;
            source.close();
            source = null;
            bytes.clear();
            retainedBytes = 0;
        }
        return stats();
    }

    synchronized Stats stats() {
        return new Stats(reads, hits, bypasses, failures, evictions, peakBytes,
                bytes.size(), retainedBytes, stopped);
    }

    // 除 transform 的读穿查询外，表的成员、迭代与修改仍只代表原入口补丁。
    @Override public Set<Entry<String, byte[]>> entrySet() { return patches.entrySet(); }
    @Override public boolean containsKey(Object key) { return patches.containsKey(key); }
    @Override public int size() { return patches.size(); }
    @Override public byte[] put(String key, byte[] value) { return patches.put(key, value); }
    @Override public byte[] remove(Object key) { return patches.remove(key); }
    @Override public void clear() { patches.clear(); }
}
